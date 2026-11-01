package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import tools.jackson.databind.ObjectMapper;
import com.litemall.domain.PO.Coupon;
import com.litemall.domain.PO.CouponUser;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CouponReceiveResultVO;
import com.litemall.domain.VO.CouponUserVO;
import com.litemall.domain.VO.CouponVO;
import com.litemall.domain.VO.UsableCouponVO;
import com.litemall.domain.VO.UsableCouponsVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.UnauthorizedException;
import com.litemall.mapper.CouponMapper;
import com.litemall.mapper.CouponUserMapper;
import com.litemall.mq.CouponReceiveProducer;
import com.litemall.service.ICouponService;
import com.litemall.utils.BeanUtils;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券信息及规则表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 领券防刷限流：同用户同券 60 秒内仅允许一次 */
    private static final Duration RECEIVE_INTERVAL = Duration.ofSeconds(60);
    private static final String RECEIVE_LOCK_KEY = "coupon:receive:";
    private static final String STOCK_KEY = "coupon:stock:";
    private static final String RECEIVED_KEY = "coupon:received:";
    private static final String RESULT_KEY = "coupon:result:";
    /**
     * M6：Redis 库存预减台账。与"预减库存"在同一步写入，记录"已从 Redis 预减、但尚未落库确认"的份数。
     * 异常回补只认这份台账（即本次实际预减量），不再依赖"当前 DB total > 0"这类事后状态判断。
     */
    private static final String STOCK_RESERVED_KEY = "coupon:stock:reserved:";
    /** 预减台账保留时长：需覆盖 MQ 消息在重试队列中的滞留时间，取 1 天 */
    private static final Duration STOCK_RESERVED_TTL = Duration.ofDays(1);
    /** M5：限领计数 TTL 下限，避免券临近过期时 TTL≤0 让限领计数立即失效 */
    private static final Duration RECEIVE_LIMIT_TTL_FLOOR = Duration.ofHours(1);
    /** M5：券没有任何周期字段（永久券且未配置 days）时的兜底限领 TTL，沿用历史行为 1 天 */
    private static final Duration RECEIVE_LIMIT_TTL_DEFAULT = Duration.ofDays(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final CouponUserMapper couponUserMapper;
    private final CouponReceiveProducer producer;
    private final ObjectMapper objectMapper;

    @Override
    public PageVO<CouponVO> getCouponList(Integer pageNum, Integer pageSize) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<Coupon> page = new Page<>(pn, ps);
        LocalDateTime now = LocalDateTime.now();
        // 当前可领取 = 未删除 + 正常状态(0) + 用户领取类型(0)
        // 且时间上：永久券(start/end 任一为 NULL) 或 限时券(now 在有效期内)
        Page<Coupon> couponPage = lambdaQuery()
                .eq(Coupon::getDeleted, false)
                .eq(Coupon::getStatus, 0)
                .eq(Coupon::getType, 0)
                .and(w -> w
                        .and(p -> p.isNull(Coupon::getStartTime).or().isNull(Coupon::getEndTime))
                        .or(p -> p.le(Coupon::getStartTime, now).ge(Coupon::getEndTime, now)))
                .page(page);
        List<Coupon> couponList = couponPage.getRecords();
        List<CouponVO> couponVOList = couponList.stream().map(coupon -> {
            CouponVO vo = BeanUtils.copyBean(coupon, CouponVO.class);
            vo.setLeftCount(coupon.getTotal());
            // PO 是 LocalDateTime，VO 是 String，手动格式化（永久券为 null）
            vo.setStartTime(coupon.getStartTime() == null ? null : coupon.getStartTime().format(DT));
            vo.setEndTime(coupon.getEndTime() == null ? null : coupon.getEndTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(couponPage.getTotal(), couponPage.getPages(), couponVOList);
    }

    @Override
    public CouponReceiveResultVO receiveCoupon(Long couponId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }

        // 0.一人一券：uk_coupon_user(coupon_id,user_id) 已建，台账里已有该券（含已使用/已过期/已软删）
        // 即视为「已领过」。必须在防刷锁之前判定，否则重复领取会被「领取过于频繁」掩盖，
        // 与「已领完」也无法区分（08 §4.2：两者文案必须可区分，且不得报 500）
        Long received = couponUserMapper.selectCount(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getCouponId, couponId)
                .eq(CouponUser::getUserId, userId));
        if (received != null && received > 0) {
            throw new BadRequestException("你已领过这张券");
        }

        // 1.防刷：同用户同券 60s 内仅允许一次
        String lockKey = RECEIVE_LOCK_KEY + couponId + ":" + userId;
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RECEIVE_INTERVAL.getSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(ok)) {
            throw new BadRequestException("领取过于频繁，请稍后再试");
        }

        // 2.校验券本身
        Coupon coupon = getById(couponId);
        if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())) {
            throw new BadRequestException("优惠券不存在");
        }
        if (coupon.getStatus() != null && coupon.getStatus() != 0) {
            throw new BadRequestException("优惠券已下架或已过期");
        }
        if (coupon.getType() != null && coupon.getType() != 0) {
            throw new BadRequestException("该优惠券不可主动领取");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean timeLimited = coupon.getTimeType() != null && coupon.getTimeType() == 1;
        if (timeLimited) {
            if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())
                    || coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
                throw new BadRequestException("优惠券未开始或已过期");
            }
        }

        // 3.限领计数（Redis）
        int limit = coupon.getLimit() == null ? 1 : coupon.getLimit();
        String receivedKey = RECEIVED_KEY + couponId + ":" + userId;
        if (limit > 0) {
            Long cnt = stringRedisTemplate.opsForValue().increment(receivedKey);
            if (cnt != null && cnt == 1L) {
                // M5：首次预增时才设置过期时间；TTL 与券自身的限领周期对齐（依据见 resolveReceiveLimitTtl），
                // 不再写死 1 天
                stringRedisTemplate.expire(receivedKey,
                        resolveReceiveLimitTtl(coupon, now).getSeconds(), TimeUnit.SECONDS);
            }
            if (cnt != null && cnt > limit) {
                // 一人一券券（limit=1）超领即「已领过」，与「已领完」区分（08 §4.2）
                throw new BadRequestException(limit == 1 ? "你已领过这张券" : "已达到最大领取数量");
            }
        }

        // 4.Redis 预减库存（total=0 无限量则跳过）
        int total = coupon.getTotal() == null ? 0 : coupon.getTotal();
        String stockKey = STOCK_KEY + couponId;
        // M6：本次是否真的预减了 1 份库存（只有真预减过才在失败时回补）
        boolean stockPreDeducted = false;
        if (total > 0) {
            // lazy 初始化：key 不存在时从 DB total 写入
            Boolean hasStock = stringRedisTemplate.hasKey(stockKey);
            if (!Boolean.TRUE.equals(hasStock)) {
                stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(total));
            }
            // M6：先登记预减台账，再扣库存 —— "扣了多少"始终有据可查
            markStockReserved(couponId, userId);
            Long left = stringRedisTemplate.opsForValue().decrement(stockKey);
            if (left == null || left < 0) {
                // 库存不足：按台账撤销本次预减（消费台账 + 回补库存 1 份），与 DB 口径保持一致
                rollbackStockPreDeduction(couponId, userId);
                throw new BadRequestException("优惠券已领完");
            }
            stockPreDeducted = true;
        }

        // 5.写 PROCESSING 结果，发 MQ
        String resultKey = RESULT_KEY + couponId + ":" + userId;
        CouponReceiveResultVO pending = new CouponReceiveResultVO();
        pending.setStatus(CouponReceiveResultVO.PROCESSING);
        try {
            stringRedisTemplate.opsForValue().set(resultKey, objectMapper.writeValueAsString(pending),
                    5, TimeUnit.MINUTES);
            producer.send(couponId, userId);
        } catch (Exception e) {
            // M5：发消息失败 → 回滚限领计数（与库存回补对称，避免用户被白扣一次领取额度）
            if (limit > 0) {
                rollbackReceiveCount(receivedKey);
            }
            // M6：按本次实际预减量回补库存（stockPreDeducted 即"本次确实预减过 1 份"）
            if (stockPreDeducted) {
                rollbackStockPreDeduction(couponId, userId);
            }
            log.error("领券发 MQ 失败: couponId={}, userId={}", couponId, userId, e);
            throw new BadRequestException("领取失败，请稍后再试");
        }
        return pending;
    }

    @Override
    public CouponReceiveResultVO getReceiveResult(Long couponId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        String resultKey = RESULT_KEY + couponId + ":" + userId;
        String json = stringRedisTemplate.opsForValue().get(resultKey);
        if (json == null) {
            CouponReceiveResultVO vo = new CouponReceiveResultVO();
            vo.setStatus(CouponReceiveResultVO.PROCESSING);
            return vo;
        }
        try {
            return objectMapper.readValue(json, CouponReceiveResultVO.class);
        } catch (Exception e) {
            throw new BadRequestException("结果解析失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persistReceive(Long couponId, Long userId) {
        String resultKey = RESULT_KEY + couponId + ":" + userId;
        try {
            Coupon coupon = getById(couponId);
            if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())
                    || coupon.getStatus() != null && coupon.getStatus() != 0) {
                // M6：没有出券 → 按本次实际预减量回补 Redis 库存，否则该份库存永久漂移
                rollbackStockPreDeduction(couponId, userId);
                writeFail(resultKey, "优惠券已不可领取");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            boolean timeLimited = coupon.getTimeType() != null && coupon.getTimeType() == 1;
            if (timeLimited && (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime()))) {
                // M6：同上，未出券即回补本次预减量
                rollbackStockPreDeduction(couponId, userId);
                writeFail(resultKey, "优惠券已过期");
                return;
            }

            // 一人一券兜底（MQ 重投/并发）：台账里已有该券时不再出券；DB total 尚未扣减，无需回补
            Long existed = couponUserMapper.selectCount(new LambdaQueryWrapper<CouponUser>()
                    .eq(CouponUser::getCouponId, couponId)
                    .eq(CouponUser::getUserId, userId));
            if (existed != null && existed > 0) {
                rollbackStockPreDeduction(couponId, userId);
                writeFail(resultKey, "你已领过这张券");
                return;
            }

            // DB 原子扣库存兜底
            int total = coupon.getTotal() == null ? 0 : coupon.getTotal();
            if (total > 0) {
                boolean deducted = lambdaUpdate()
                        .setSql("total = total - 1")
                        .eq(Coupon::getId, couponId)
                        .gt(Coupon::getTotal, 0)
                        .update();
                if (!deducted) {
                    // M6：DB 已扣到 0（并发下 total 恰好耗尽）时更要回补，否则 Redis 库存被白扣
                    rollbackStockPreDeduction(couponId, userId);
                    writeFail(resultKey, "优惠券已领完");
                    return;
                }
            }

            LocalDateTime startTime = timeLimited ? coupon.getStartTime() : now;
            LocalDateTime endTime = timeLimited ? coupon.getEndTime()
                    : (coupon.getDays() == null ? null : now.plusDays(coupon.getDays()));

            CouponUser record = new CouponUser()
                    .setUserId(userId)
                    .setCouponId(couponId)
                    .setStatus((short) 0)
                    .setStartTime(startTime)
                    .setEndTime(endTime)
                    .setAddTime(now)
                    .setUpdateTime(now)
                    .setDeleted(false);
            try {
                couponUserMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // 一人一券：uk_coupon_user(coupon_id, user_id) 撞键说明并发/重投下该用户已领过这张券。
                // 必须与「优惠券已领完」文案可区分，且不得冒泡成 500 或「系统异常，领取失败」（08 §4.2）
                log.warn("一人一券拦截: couponId={}, userId={}", couponId, userId);
                if (total > 0) {
                    // 上面已原子扣减过 total，撞键即未出券，需回补
                    lambdaUpdate()
                            .setSql("total = total + 1")
                            .eq(Coupon::getId, couponId)
                            .update();
                }
                rollbackStockPreDeduction(couponId, userId);
                writeFail(resultKey, "你已领过这张券");
                return;
            }

            CouponUserVO vo = new CouponUserVO();
            vo.setId(record.getId());
            vo.setCouponId(couponId);
            vo.setName(coupon.getName());
            vo.setDiscount(coupon.getDiscount());
            vo.setMin(coupon.getMin());
            vo.setEndTime(endTime == null ? null : endTime.format(DT));

            CouponReceiveResultVO result = new CouponReceiveResultVO();
            result.setStatus(CouponReceiveResultVO.SUCCESS);
            result.setCouponUser(vo);
            stringRedisTemplate.opsForValue().set(resultKey, objectMapper.writeValueAsString(result),
                    1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("领券落库失败: couponId={}, userId={}", couponId, userId, e);
            // M6：回补 Redis 库存只认"预减台账"（本次实际预减量），不再用"当前 DB total > 0"这种事后状态判断，
            // 否则 total 恰被扣到 0 时不会回补，Redis 与 DB 库存会永久漂移
            rollbackStockPreDeduction(couponId, userId);
            writeFail(resultKey, "系统异常，领取失败");
        }
    }

    /**
     * M5：计算限领计数（{@link #RECEIVED_KEY}）的过期时间，与券自身的限领周期对齐。
     * 判断依据（按优先级）：
     * 1) timeType=1（start_time/end_time 构成一套固定有效期）：限领周期就是这套有效期窗口，
     *    TTL 取"券结束时间 - 当前时间"，即计数只需活到券失效为止；
     * 2) timeType=0（自领取时间起 days 天内有效）：券的有效期本身就是限领周期，TTL 取 days 天；
     * 3) 其余情况（如永久券未配置 days、end_time 为空）：没有明确周期字段，回退兜底 1 天。
     * 计算出的 TTL 一律不低于 {@link #RECEIVE_LIMIT_TTL_FLOOR}，避免券临近过期时 TTL≤0 让限领计数立即失效。
     */
    private Duration resolveReceiveLimitTtl(Coupon coupon, LocalDateTime now) {
        Duration ttl = null;
        if (coupon.getTimeType() != null && coupon.getTimeType() == 1 && coupon.getEndTime() != null) {
            ttl = Duration.between(now, coupon.getEndTime());
        } else if (coupon.getDays() != null && coupon.getDays() > 0) {
            ttl = Duration.ofDays(coupon.getDays());
        }
        if (ttl == null) {
            return RECEIVE_LIMIT_TTL_DEFAULT;
        }
        return ttl.compareTo(RECEIVE_LIMIT_TTL_FLOOR) < 0 ? RECEIVE_LIMIT_TTL_FLOOR : ttl;
    }

    /**
     * M5：回滚一次限领计数，与领取时的 increment 严格对称（本次只预增了 1 次）。
     * 扣减后 ≤0 时直接删除 key：既不残留 0 值/负值脏计数，也让重复调用保持幂等（幂等性依赖"只扣自己预增的那 1 次"，
     * 同用户同券有 60 秒领取锁串行保护）。
     * 仅在"未真正发出领券请求"的失败路径调用，正常成功路径不受影响。
     */
    private void rollbackReceiveCount(String receivedKey) {
        try {
            Long cnt = stringRedisTemplate.opsForValue().decrement(receivedKey);
            if (cnt != null && cnt <= 0L) {
                stringRedisTemplate.delete(receivedKey);
            }
        } catch (Exception e) {
            log.error("回滚限领计数失败: key={}", receivedKey, e);
        }
    }

    /** M6：登记一份"已预减待确认"的库存台账（在预减库存前调用，与预减动作一一对应） */
    private void markStockReserved(Long couponId, Long userId) {
        String reservedKey = STOCK_RESERVED_KEY + couponId + ":" + userId;
        Long cnt = stringRedisTemplate.opsForValue().increment(reservedKey);
        if (cnt != null && cnt == 1L) {
            stringRedisTemplate.expire(reservedKey, STOCK_RESERVED_TTL.getSeconds(), TimeUnit.SECONDS);
        }
    }

    /**
     * M6：按"本次实际预减量"回补 Redis 库存：先原子消费一条预减台账，只有确实存在未回补的预减记录才回补 1 份。
     * 因此可重复调用（MQ 重复投递、多条失败出口叠加）不会多补；台账被扣成负数说明本次没有可回补的量，
     * 此时把台账修正回 0 并放弃回补。
     *
     * @return 是否真的发生了库存回补
     */
    private boolean rollbackStockPreDeduction(Long couponId, Long userId) {
        if (!consumeStockReservation(couponId, userId)) {
            return false;
        }
        stringRedisTemplate.opsForValue().increment(STOCK_KEY + couponId);
        return true;
    }

    /** M6：原子消费一条库存预减台账（可重复调用，消费不到时不会留下负数脏值） */
    private boolean consumeStockReservation(Long couponId, Long userId) {
        String reservedKey = STOCK_RESERVED_KEY + couponId + ":" + userId;
        try {
            Long left = stringRedisTemplate.opsForValue().decrement(reservedKey);
            if (left == null) {
                return false;
            }
            if (left < 0L) {
                // 没有可消费的预减记录：把台账修正回 0，避免留下负数脏值
                stringRedisTemplate.delete(reservedKey);
                return false;
            }
            if (left == 0L) {
                stringRedisTemplate.delete(reservedKey);
            }
            return true;
        } catch (Exception e) {
            // Redis 异常时保守放弃：宁可少回补，也不能多补（多补会导致超发）
            log.error("消费库存预减台账失败: couponId={}, userId={}", couponId, userId, e);
            return false;
        }
    }

    private void writeFail(String resultKey, String msg) {
        CouponReceiveResultVO fail = new CouponReceiveResultVO();
        fail.setStatus(CouponReceiveResultVO.FAILED);
        fail.setMessage(msg);
        try {
            stringRedisTemplate.opsForValue().set(resultKey, objectMapper.writeValueAsString(fail),
                    1, TimeUnit.HOURS);
        } catch (Exception ignored) {}
    }

    @Override
    public PageVO<CouponUserVO> getCouponUserList(Integer pageNum, Integer pageSize, String status) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<CouponUser> page = new Page<>(pn, ps);
        Page<CouponUser> couponUserPage = couponUserMapper.selectPage(page,
                new LambdaQueryWrapper<CouponUser>()
                        .eq(CouponUser::getUserId, UserContext.getUser())
                        .eq(CouponUser::getDeleted, false)
                        .eq(status != null, CouponUser::getStatus, status));
        List<CouponUser> couponUserList = couponUserPage.getRecords();
        if (couponUserList.isEmpty()) {
            return new PageVO<>(couponUserPage.getTotal(), couponUserPage.getPages(), List.of());
        }

        // 批量查询券信息，补充 name/discount/min
        List<Long> couponIds = couponUserList.stream()
                .map(c -> c.getCouponId())
                .collect(Collectors.toList());
        Map<Long, Coupon> couponMap = listByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c, (a, b) -> a));

        List<CouponUserVO> couponUserVOList = couponUserList.stream().map(couponUser -> {
            CouponUserVO vo = BeanUtils.copyBean(couponUser, CouponUserVO.class);
            Coupon coupon = couponMap.get(couponUser.getCouponId());
            if (coupon != null) {
                vo.setName(coupon.getName());
                vo.setDiscount(coupon.getDiscount());
                vo.setMin(coupon.getMin());
            }
            vo.setEndTime(couponUser.getEndTime() == null ? null : couponUser.getEndTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(couponUserPage.getTotal(), couponUserPage.getPages(), couponUserVOList);
    }

    /**
     * CN-19：可用券试算。只读，不占券、不改任何状态。
     * <p>
     * 展示范围按 03 卡 D-14 冻结口径：仅「券本身正常（coupon.status=0）、我的券未使用（coupon_user.status=0）、
     * 未过期」的券进入列表；其余券不展示，因此主要可命中的不可用原因只有「未到使用时间」与「差 X 元可用」，
     * 另保留「已过期」防御分支（券自身 time_type=1 且 coupon.end_time 已过，而 coupon_user.end_time 为空的情形）。
     * </p>
     * <p>
     * 排序：可用在前 → 券面额降序 → 到期时间升序（空值列末）；deductAmount 不可用时为 0，且恒不超过 goodsAmount。
     * </p>
     */
    @Override
    public UsableCouponsVO getUsableCoupons(Integer goodsAmountCents) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        if (goodsAmountCents == null || goodsAmountCents < 0) {
            throw new BadRequestException("商品金额不合法", BadRequestException.PARAM_INVALID);
        }

        UsableCouponsVO result = new UsableCouponsVO();
        result.setGoodsAmount(goodsAmountCents);
        result.setCoupons(List.of());

        List<CouponUser> couponUsers = couponUserMapper.selectList(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getUserId, userId)
                .eq(CouponUser::getDeleted, false)
                .eq(CouponUser::getStatus, (short) 0));
        if (couponUsers == null || couponUsers.isEmpty()) {
            return result;
        }

        List<Long> couponIds = couponUsers.stream()
                .map(CouponUser::getCouponId)
                .filter(couponId -> couponId != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Coupon> couponMap = couponIds.isEmpty() ? Map.of()
                : listByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, coupon -> coupon, (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        List<UsableCouponVO> coupons = new ArrayList<>();
        for (CouponUser couponUser : couponUsers) {
            Coupon coupon = couponMap.get(couponUser.getCouponId());
            if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())) {
                // 券已删除：台账里的券不再对外展示
                continue;
            }
            UsableCouponVO vo = buildUsableCoupon(couponUser, coupon, goodsAmountCents, now);
            if (vo != null) {
                coupons.add(vo);
            }
        }
        coupons.sort(Comparator
                .<UsableCouponVO, Boolean>comparing(UsableCouponVO::getUsable, Comparator.reverseOrder())
                .thenComparing(UsableCouponVO::getDiscount, Comparator.reverseOrder())
                .thenComparing(UsableCouponVO::getEndTime, Comparator.nullsLast(Comparator.naturalOrder())));
        result.setCoupons(coupons);
        return result;
    }

    /**
     * 构造单张券的试算结果；不满足展示条件的券返回 null（由调用方跳过）。
     */
    private UsableCouponVO buildUsableCoupon(CouponUser couponUser, Coupon coupon,
                                            int goodsAmountCents, LocalDateTime now) {
        if (coupon.getStatus() == null || coupon.getStatus() != 0) {
            // 券已停用/已过期：不展示（D-14）
            return null;
        }
        if (couponUser.getStatus() == null || couponUser.getStatus() != 0) {
            // 已使用/已过期：不展示（D-14）
            return null;
        }
        if (couponUser.getEndTime() != null && now.isAfter(couponUser.getEndTime())) {
            return null;
        }

        int discountCents = toCents(coupon.getDiscount());
        int minCents = toCents(coupon.getMin());
        String unusableReason = null;
        if (coupon.getTimeType() != null && coupon.getTimeType() == 1
                && coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
            unusableReason = "已过期";
        } else if (couponUser.getStartTime() != null && now.isBefore(couponUser.getStartTime())) {
            unusableReason = "未到使用时间";
        } else if (goodsAmountCents < minCents) {
            unusableReason = "差 " + centsToYuanText(minCents - goodsAmountCents) + " 元可用";
        }
        boolean usable = unusableReason == null;

        UsableCouponVO vo = new UsableCouponVO();
        vo.setCouponUserId(couponUser.getId());
        vo.setCouponId(couponUser.getCouponId());
        vo.setName(coupon.getName());
        vo.setDiscount(discountCents);
        vo.setMin(minCents);
        vo.setEndTime(couponUser.getEndTime() == null ? null : couponUser.getEndTime().format(DT));
        vo.setUsable(usable);
        vo.setUnusableReason(unusableReason);
        vo.setDeductAmount(usable ? Math.min(discountCents, goodsAmountCents) : 0);
        return vo;
    }

    /**
     * 元 → 分（仅接口边界使用，库内仍为 DECIMAL 元）
     */
    private int toCents(BigDecimal yuan) {
        if (yuan == null) {
            return 0;
        }
        return yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 分 → 元文本（两位小数，用于「差 X 元可用」文案）
     */
    private String centsToYuanText(int cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public BigDecimal checkCoupon(Long couponUserId, BigDecimal goodsAmount) {
        if (couponUserId == null) {
            return BigDecimal.ZERO;
        }
        CouponUser couponUser = couponUserMapper.selectById(couponUserId);
        if (couponUser == null || Boolean.TRUE.equals(couponUser.getDeleted())
                || couponUser.getStatus() == null || couponUser.getStatus() != 0) {
            return BigDecimal.ZERO;
        }
        // 有效期判断：end_time 为空视为永久券；否则必须在有效期内
        LocalDateTime now = LocalDateTime.now();
        if (couponUser.getEndTime() != null && now.isAfter(couponUser.getEndTime())) {
            return BigDecimal.ZERO;
        }
        Coupon coupon = getById(couponUser.getCouponId());
        if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())
                || coupon.getStatus() != null && coupon.getStatus() != 0) {
            return BigDecimal.ZERO;
        }
        // 门槛判断：min 为空视为无门槛
        BigDecimal min = coupon.getMin();
        if (min != null && goodsAmount != null && goodsAmount.compareTo(min) < 0) {
            return BigDecimal.ZERO;
        }
        // 抵扣金额不超过商品总额
        BigDecimal discount = coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
        if (goodsAmount != null && discount.compareTo(goodsAmount) > 0) {
            return goodsAmount;
        }
        return discount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void useCoupon(Long couponUserId, Long orderId) {
        if (couponUserId == null) {
            return;
        }
        int updated = couponUserMapper.update(null, new LambdaUpdateWrapper<CouponUser>()
                .eq(CouponUser::getId, couponUserId)
                .eq(CouponUser::getStatus, 0)
                .eq(CouponUser::getDeleted, false)
                .set(CouponUser::getStatus, (short) 1)
                .set(CouponUser::getUsedTime, LocalDateTime.now())
                .set(CouponUser::getOrderId, orderId)
                .set(CouponUser::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BadRequestException("优惠券不可用");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCoupon(Long couponUserId, Long orderId) {
        // 优先按用户券记录ID回退；否则按订单ID反查已核销的券
        if (couponUserId == null && orderId != null) {
            List<CouponUser> usedList = couponUserMapper.selectList(new LambdaQueryWrapper<CouponUser>()
                    .eq(CouponUser::getOrderId, orderId)
                    .eq(CouponUser::getStatus, 1)
                    .eq(CouponUser::getDeleted, false));
            for (CouponUser used : usedList) {
                doRollback(used.getId());
            }
            return;
        }
        if (couponUserId != null) {
            doRollback(couponUserId);
        }
    }

    private void doRollback(Long couponUserId) {
        couponUserMapper.update(null, new LambdaUpdateWrapper<CouponUser>()
                .eq(CouponUser::getId, couponUserId)
                .eq(CouponUser::getStatus, 1)
                .eq(CouponUser::getDeleted, false)
                .set(CouponUser::getStatus, (short) 0)
                .set(CouponUser::getUsedTime, null)
                .set(CouponUser::getOrderId, null)
                .set(CouponUser::getUpdateTime, LocalDateTime.now()));
    }
}

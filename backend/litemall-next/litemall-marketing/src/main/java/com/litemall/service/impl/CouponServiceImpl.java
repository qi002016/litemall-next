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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
                throw new BadRequestException("已达到最大领取数量");
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
            couponUserMapper.insert(record);

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
        List<Integer> couponIds = couponUserList.stream()
                .map(c -> c.getCouponId().intValue())
                .collect(Collectors.toList());
        Map<Integer, Coupon> couponMap = listByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c, (a, b) -> a));

        List<CouponUserVO> couponUserVOList = couponUserList.stream().map(couponUser -> {
            CouponUserVO vo = BeanUtils.copyBean(couponUser, CouponUserVO.class);
            Coupon coupon = couponMap.get(couponUser.getCouponId().intValue());
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

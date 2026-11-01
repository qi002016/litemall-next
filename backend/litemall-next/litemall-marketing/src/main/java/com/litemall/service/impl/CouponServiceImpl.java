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
import com.litemall.exception.NotFoundException;
import com.litemall.exception.UnauthorizedException;
import com.litemall.mapper.CouponMapper;
import com.litemall.mapper.CouponUserMapper;
import com.litemall.service.ICouponService;
import com.litemall.utils.BeanUtils;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

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

    /** 领券防刷限流：同用户同券 60 秒内仅允许一次（05 §2.4 判定第 4 步） */
    private static final Duration RECEIVE_INTERVAL = Duration.ofSeconds(60);
    private static final String RECEIVE_LOCK_KEY = "coupon:receive:";
    private static final String STOCK_KEY = "coupon:stock:";
    private static final String RESULT_KEY = "coupon:result:";
    /**
     * Redis 库存预减台账。与"预减库存"在同一步写入，记录"已从 Redis 预减、但尚未落库确认"的份数。
     * 失败回补只认这份台账（即本次实际预减量），不再依赖"当前 DB total > 0"这类事后状态判断。
     */
    private static final String STOCK_RESERVED_KEY = "coupon:stock:reserved:";
    /** 预减台账保留时长：需覆盖一次领券事务的失败回补窗口，取 1 天 */
    private static final Duration STOCK_RESERVED_TTL = Duration.ofDays(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final CouponUserMapper couponUserMapper;
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
            // CI-53：leftCount = 剩余可领数量（= 发放上限 - 已发放数）；total=0 表示不限量，返回 -1
            Integer total = coupon.getTotal();
            long issued = total == null || total <= 0 ? 0L : countIssued(coupon.getId());
            vo.setLeftCount(total == null || total <= 0 ? -1 : (int) Math.max(0L, total - issued));
            // PO 是 LocalDateTime，VO 是 String，手动格式化（永久券为 null）
            vo.setStartTime(coupon.getStartTime() == null ? null : coupon.getStartTime().format(DT));
            vo.setEndTime(coupon.getEndTime() == null ? null : coupon.getEndTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(couponPage.getTotal(), couponPage.getPages(), couponVOList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponReceiveResultVO receiveCoupon(Long couponId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }

        // 1.锁定券行：四态必须按最新的 litemall_coupon 判定（停用/售罄可能刚被后台改掉），
        //   且与 coupon_user 落库同事务，避免"先查后写"之间被并发改写而超发
        Coupon coupon = getOne(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .last("FOR UPDATE"));
        if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())) {
            // 05 §2.4 判定第 2 步：券不存在/已删除 ⇒ 404 RESOURCE_NOT_FOUND（data=null）
            throw new NotFoundException("优惠券不存在");
        }

        // 2.一人一券：uk_coupon_user(coupon_id,user_id) 下已有记录（含已使用/已过期/已软删）即「已领过」
        if (countIssuedByUser(couponId, userId) > 0) {
            return state(CouponReceiveResultVO.RESULT_ALREADY_RECEIVED, "你已领过这张券");
        }

        // 3.防刷：同用户同券 60s 内仅允许一次（不属四态 ⇒ data=null，走异常）
        String lockKey = RECEIVE_LOCK_KEY + couponId + ":" + userId;
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RECEIVE_INTERVAL.getSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(ok)) {
            throw new BadRequestException("领取过于频繁，请稍后再试");
        }

        // 4.券不可领取 ⇒ disabled（停用 / 不可主动领取 / 不在有效期，文案不得混用）
        if (coupon.getStatus() != null && coupon.getStatus() != 0) {
            return state(CouponReceiveResultVO.RESULT_DISABLED, "该券已停用");
        }
        if (coupon.getType() != null && coupon.getType() != 0) {
            return state(CouponReceiveResultVO.RESULT_DISABLED, "该券已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean timeLimited = coupon.getTimeType() != null && coupon.getTimeType() == 1;
        if (timeLimited) {
            if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())
                    || coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
                return state(CouponReceiveResultVO.RESULT_DISABLED, "该券不在有效期");
            }
        }

        // 5.售罄：total>0 且已发放数达上限（已发放数取 DB 计数，Redis 库存只作预减闸门）
        int total = coupon.getTotal() == null ? 0 : coupon.getTotal();
        if (total > 0 && countIssued(couponId) >= total) {
            return state(CouponReceiveResultVO.RESULT_SOLD_OUT, "已领完");
        }

        // 6.Redis 预减库存（total=0 不限量则跳过）
        if (total > 0) {
            String stockKey = STOCK_KEY + couponId;
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
                stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(total));
            }
            markStockReserved(couponId, userId);
            Long left = stringRedisTemplate.opsForValue().decrement(stockKey);
            if (left == null || left < 0) {
                rollbackStockPreDeduction(couponId, userId);
                return state(CouponReceiveResultVO.RESULT_SOLD_OUT, "已领完");
            }
        }

        // 7.同步落库：CI-54 要求本次响应即带 couponUser（R1 的 MQ 异步路径已移除）
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
            // uk_coupon_user 兜底并发重复领取：必须收敛成 already_received，绝不冒泡 500
            // （BR-CP-02：already_received 与 sold_out 文案不得混用）。撞键后事务已 aborted，不能再发 SQL
            log.warn("一人一券拦截(并发重复领取): couponId={}, userId={}", couponId, userId);
            if (total > 0) {
                rollbackStockPreDeduction(couponId, userId);
            }
            markRollbackOnly();
            return state(CouponReceiveResultVO.RESULT_ALREADY_RECEIVED, "你已领过这张券");
        }

        // 8.插入后复核发放上限：并发窗口内可能多出券，超出则软删本行并回补库存（05 §2.4.3）
        if (total > 0 && countIssued(couponId) > total) {
            log.warn("超发拦截: couponId={}, userId={}", couponId, userId);
            couponUserMapper.update(null, new LambdaUpdateWrapper<CouponUser>()
                    .eq(CouponUser::getId, record.getId())
                    .set(CouponUser::getDeleted, true)
                    .set(CouponUser::getUpdateTime, LocalDateTime.now()));
            rollbackStockPreDeduction(couponId, userId);
            return state(CouponReceiveResultVO.RESULT_SOLD_OUT, "已领完");
        }

        // 9.成功：回写 CI-55 轮询结果并返回四态 success（带 couponUser）
        CouponUserVO couponUserVO = new CouponUserVO();
        couponUserVO.setId(record.getId());
        couponUserVO.setCouponId(couponId);
        couponUserVO.setName(coupon.getName());
        couponUserVO.setDiscount(coupon.getDiscount());
        couponUserVO.setMin(coupon.getMin());
        couponUserVO.setEndTime(endTime == null ? null : endTime.format(DT));

        CouponReceiveResultVO success = state(CouponReceiveResultVO.RESULT_SUCCESS, "领取成功");
        success.setCouponUser(couponUserVO);
        cacheReceiveResult(couponId, userId, success);
        return success;
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

    /**
     * 标记当前事务回滚。撞键后 PostgreSQL 事务已 aborted，必须回滚而不能继续发 SQL；
     * 非事务上下文（单测/直调）下没有可回滚的事务，忽略即可。
     */
    private void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException e) {
            log.debug("无事务上下文，跳过回滚标记");
        }
    }

    /** 构造四态响应（message 与响应 msg 一致，05 §2.4） */
    private CouponReceiveResultVO state(String result, String message) {
        CouponReceiveResultVO vo = new CouponReceiveResultVO();
        vo.setResult(result);
        vo.setMessage(message);
        return vo;
    }

    /** 已发放数：coupon_user 中该券未删除的记录数（软删的本行不计入） */
    private long countIssued(Long couponId) {
        Long cnt = couponUserMapper.selectCount(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getCouponId, couponId)
                .eq(CouponUser::getDeleted, false));
        return cnt == null ? 0L : cnt;
    }

    /** 该用户在该券下的记录数（含已使用/已过期/已软删，用于「已领过」判定） */
    private long countIssuedByUser(Long couponId, Long userId) {
        Long cnt = couponUserMapper.selectCount(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getCouponId, couponId)
                .eq(CouponUser::getUserId, userId));
        return cnt == null ? 0L : cnt;
    }

    /** 回写 CI-55 轮询结果；失败只 WARN，不影响本次领取 */
    private void cacheReceiveResult(Long couponId, Long userId, CouponReceiveResultVO vo) {
        try {
            stringRedisTemplate.opsForValue().set(RESULT_KEY + couponId + ":" + userId,
                    objectMapper.writeValueAsString(vo), 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("写领券结果缓存失败: couponId={}, userId={}", couponId, userId, e);
        }
    }

}

package com.litemall.service;

import com.litemall.domain.PO.Coupon;
import com.litemall.domain.PO.CouponUser;
import com.litemall.domain.VO.CouponReceiveResultVO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CouponMapper;
import com.litemall.mapper.CouponUserMapper;
import com.litemall.mq.CouponReceiveProducer;
import com.litemall.service.impl.CouponServiceImpl;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>
 * 领券服务单元测试（纯 Mockito，不加载 Spring 上下文，不依赖真实 Redis / MQ / MySQL）
 * </p>
 * <p>
 * 覆盖 M5（MQ 发送失败回滚限领计数、限领 TTL 与券规则对齐）与
 * M6（异常回补以"本次实际预减量"为准，而非"当前 DB total > 0"）。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    private static final long COUPON_ID = 101L;
    private static final long USER_ID = 7L;

    private static final String RECEIVE_LOCK_KEY = "coupon:receive:" + COUPON_ID + ":" + USER_ID;
    private static final String STOCK_KEY = "coupon:stock:" + COUPON_ID;
    private static final String RECEIVED_KEY = "coupon:received:" + COUPON_ID + ":" + USER_ID;
    private static final String RESERVED_KEY = "coupon:stock:reserved:" + COUPON_ID + ":" + USER_ID;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CouponUserMapper couponUserMapper;

    @Mock
    private CouponReceiveProducer producer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CouponMapper couponMapper;

    private CouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        UserContext.setUser(USER_ID);
        // ServiceImpl 的 baseMapper 为父类 protected 字段，Mockito @InjectMocks 无法注入，显式设置
        couponService = new CouponServiceImpl(stringRedisTemplate, couponUserMapper, producer, objectMapper);
        ReflectionTestUtils.setField(couponService, "baseMapper", couponMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /** M5 + M6：MQ 发送失败时，限领计数必须回滚（与库存回补对称），库存按实际预减量回补 */
    @Test
    @DisplayName("M5：MQ 发送失败时回滚限领计数并按实际预减量回补库存")
    void receiveCoupon_mqSendFails_rollsBackLimitCountAndStock() {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon(10));
        ValueOperations<String, String> ops = stubValueOps();
        when(ops.setIfAbsent(RECEIVE_LOCK_KEY, "1", 60L, TimeUnit.SECONDS)).thenReturn(true);
        when(ops.increment(RECEIVED_KEY)).thenReturn(1L);
        when(stringRedisTemplate.hasKey(STOCK_KEY)).thenReturn(true);
        when(ops.increment(RESERVED_KEY)).thenReturn(1L);
        when(ops.decrement(STOCK_KEY)).thenReturn(9L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        // 回滚：先消费预减台账（返回 0 表示刚消费掉最后一份预减记录），再回滚限领计数（归零）
        when(ops.decrement(RESERVED_KEY)).thenReturn(0L);
        when(ops.decrement(RECEIVED_KEY)).thenReturn(0L);
        doThrow(new IllegalStateException("rocketmq down")).when(producer).send(COUPON_ID, USER_ID);

        assertThrows(BadRequestException.class, () -> couponService.receiveCoupon(COUPON_ID));

        // M5：预增的 1 次限领额度被扣回，且归零后删除 key（不留 0 值脏计数）
        verify(ops).decrement(RECEIVED_KEY);
        verify(stringRedisTemplate).delete(RECEIVED_KEY);
        // M6：库存按台账回补 1 份
        verify(ops).increment(STOCK_KEY);
    }

    /** 正常成功路径行为不变：不回滚限领计数、不回补库存，仍返回 PROCESSING */
    @Test
    @DisplayName("正常路径：领取成功时权重不变，仅预增计数与预减库存")
    void receiveCoupon_success_keepsLimitCountAndStock() {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon(10));
        ValueOperations<String, String> ops = stubValueOps();
        when(ops.setIfAbsent(RECEIVE_LOCK_KEY, "1", 60L, TimeUnit.SECONDS)).thenReturn(true);
        when(ops.increment(RECEIVED_KEY)).thenReturn(1L);
        when(stringRedisTemplate.hasKey(STOCK_KEY)).thenReturn(true);
        when(ops.increment(RESERVED_KEY)).thenReturn(1L);
        when(ops.decrement(STOCK_KEY)).thenReturn(9L);

        CouponReceiveResultVO vo = couponService.receiveCoupon(COUPON_ID);

        assertEquals(CouponReceiveResultVO.PROCESSING, vo.getStatus());
        verify(producer).send(COUPON_ID, USER_ID);
        verify(ops).increment(RECEIVED_KEY);
        verify(ops).decrement(STOCK_KEY);
        // 成功路径不得出现任何补偿动作
        verify(ops, never()).decrement(RECEIVED_KEY);
        verify(ops, never()).decrement(RESERVED_KEY);
        verify(ops, never()).increment(STOCK_KEY);
    }

    /** M5：限时券（timeType=1）限领 TTL = 券结束时间 - 当前时间 */
    @Test
    @DisplayName("M5：限时券限领 TTL 与券 endTime 对齐")
    void receiveCoupon_timeLimitedCoupon_limitTtlAlignedToEndTime() {
        LocalDateTime endTime = LocalDateTime.now().plusHours(3);
        Coupon limited = coupon(10)
                .setTimeType(1)
                .setStartTime(LocalDateTime.now().minusDays(1))
                .setEndTime(endTime);
        stubReceiveCouponForTtl(limited);

        LocalDateTime before = LocalDateTime.now();
        couponService.receiveCoupon(COUPON_ID);
        LocalDateTime after = LocalDateTime.now();

        long ttl = captureLimitTtl();
        long upper = Duration.between(before, endTime).getSeconds();
        long lower = Duration.between(after, endTime).getSeconds();
        assertTrue(ttl >= lower && ttl <= upper,
                "限领 TTL 应约等于 endTime - now(约 3 小时)，实际=" + ttl);
        assertTrue(ttl < 86400L, "不应再写死 1 天，实际=" + ttl);
        verify(valueOperations).increment(RECEIVED_KEY);
    }

    /** M5：基于领取时间的券（timeType=0）限领 TTL = days 天 */
    @Test
    @DisplayName("M5：按领取天数生效的券，限领 TTL 等于 days 天")
    void receiveCoupon_daysBasedCoupon_limitTtlEqualsDays() {
        stubReceiveCouponForTtl(coupon(10).setTimeType(0).setDays(30));

        couponService.receiveCoupon(COUPON_ID);

        assertEquals(30L * 86400L, captureLimitTtl().longValue());
    }

    /** M5：券即将过期时 TTL 取合理下限（1 小时），避免 TTL≤0 让限领计数立即失效 */
    @Test
    @DisplayName("M5：限领 TTL 保留 1 小时下限")
    void receiveCoupon_expiringSoon_limitTtlKeepsFloor() {
        Coupon expiring = coupon(10)
                .setTimeType(1)
                .setStartTime(LocalDateTime.now().minusDays(1))
                .setEndTime(LocalDateTime.now().plusMinutes(5));
        stubReceiveCouponForTtl(expiring);

        couponService.receiveCoupon(COUPON_ID);

        assertEquals(3600L, captureLimitTtl().longValue());
    }

    /** M5：券没有可用周期字段时回退兜底 1 天 */
    @Test
    @DisplayName("M5：无周期字段的永久券限领 TTL 回退 1 天")
    void receiveCoupon_noPeriodFields_limitTtlFallsBackToDefault() {
        stubReceiveCouponForTtl(coupon(10).setTimeType(1).setEndTime(null).setDays(null));

        couponService.receiveCoupon(COUPON_ID);

        assertEquals(86400L, captureLimitTtl().longValue());
    }

    /** M6 核心回归：DB total 恰好被扣到 0 时，落库异常仍按"本次预减量"回补 Redis 库存 */
    @Test
    @DisplayName("M6：DB total 扣到 0 后落库异常，仍按预减台账回补库存")
    void persistReceive_dbTotalIsZero_restoresStockByPreDeductedAmount() {
        // DB 库存已被并发扣到 0：旧实现用"当前 DB total > 0"判断，会漏掉这次回补
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon(0));
        ValueOperations<String, String> ops = stubValueOps();
        // 本次实际预减了 1 份，台账消费后归零
        when(ops.decrement(RESERVED_KEY)).thenReturn(0L);
        doThrow(new IllegalStateException("db down")).when(couponUserMapper).insert(any(CouponUser.class));

        couponService.persistReceive(COUPON_ID, USER_ID);

        verify(couponUserMapper).insert(any(CouponUser.class));
        verify(ops).increment(STOCK_KEY);
        verify(stringRedisTemplate).delete(RESERVED_KEY);
    }

    /** M6：券已下架/删除未出券时，同样按预减台账回补库存 */
    @Test
    @DisplayName("M6：券不可领取（未出券）时按预减台账回补库存")
    void persistReceive_couponUnavailable_restoresStock() {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon(5).setStatus(2));
        ValueOperations<String, String> ops = stubValueOps();
        when(ops.decrement(RESERVED_KEY)).thenReturn(1L);

        couponService.persistReceive(COUPON_ID, USER_ID);

        verify(ops).increment(STOCK_KEY);
        verify(couponUserMapper, never()).insert(any(CouponUser.class));
    }

    /** M6：无预减台账（MQ 重复投递）不得重复回补库存——回补必须幂等 */
    @Test
    @DisplayName("M6：无预减台账时不重复回补库存")
    void persistReceive_noReservation_doesNotRestoreTwice() {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon(0));
        ValueOperations<String, String> ops = stubValueOps();
        // 台账已被上一次回补消费掉（decrement 后为负）：本次没有可回补的量
        when(ops.decrement(RESERVED_KEY)).thenReturn(-1L);
        doThrow(new IllegalStateException("db down")).when(couponUserMapper).insert(any(CouponUser.class));

        couponService.persistReceive(COUPON_ID, USER_ID);

        verify(ops, never()).increment(STOCK_KEY);
        // 负数脏值被修正回 0
        verify(stringRedisTemplate).delete(RESERVED_KEY);
    }

    // ---------------------------------------------------------------- helpers

    /** 券默认形态：正常在用、用户可领、有限库存、按领取时间 days 天生效 */
    private Coupon coupon(int total) {
        return new Coupon()
                .setId(COUPON_ID)
                .setName("测试券")
                .setTotal(total)
                .setLimit(1)
                .setStatus(0)
                .setType(0)
                .setDeleted(false)
                .setTimeType(0)
                .setDays(30);
    }

    private ValueOperations<String, String> stubValueOps() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        return valueOperations;
    }

    /** 领券成功到"预减库存完成"所需的公共桩（不含任何补偿分支） */
    private void stubReceiveCouponForTtl(Coupon coupon) {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);
        ValueOperations<String, String> ops = stubValueOps();
        when(ops.setIfAbsent(RECEIVE_LOCK_KEY, "1", 60L, TimeUnit.SECONDS)).thenReturn(true);
        when(ops.increment(RECEIVED_KEY)).thenReturn(1L);
        when(stringRedisTemplate.hasKey(STOCK_KEY)).thenReturn(true);
        when(ops.increment(RESERVED_KEY)).thenReturn(1L);
        when(ops.decrement(STOCK_KEY)).thenReturn(9L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    private Long captureLimitTtl() {
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(stringRedisTemplate).expire(eq(RECEIVED_KEY), ttl.capture(), eq(TimeUnit.SECONDS));
        return ttl.getValue();
    }
}
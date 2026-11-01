package com.litemall.service;

import com.litemall.domain.PO.Coupon;
import com.litemall.domain.PO.CouponUser;
import com.litemall.domain.VO.CouponReceiveResultVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.NotFoundException;
import com.litemall.exception.UnauthorizedException;
import com.litemall.mapper.CouponMapper;
import com.litemall.mapper.CouponUserMapper;
import com.litemall.service.impl.CouponServiceImpl;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>
 * 领券服务单元测试（纯 Mockito，不加载 Spring 上下文，不依赖真实 Redis / MySQL）。
 * </p>
 * <p>
 * 覆盖 05 §2.4（CI-54）四态：success / already_received / sold_out / disabled，
 * 以及未登录 401、券不存在 404、频控 400（data=null）、并发撞键收敛与超发软删回补。
 * R2 已把领券从 MQ 异步改为同步出券，不再有 PROCESSING 用例。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponServiceImplTest {

    private static final long COUPON_ID = 101L;
    private static final long USER_ID = 7L;

    private static final String RECEIVE_LOCK_KEY = "coupon:receive:" + COUPON_ID + ":" + USER_ID;
    private static final String STOCK_KEY = "coupon:stock:" + COUPON_ID;
    private static final String RESERVED_KEY = "coupon:stock:reserved:" + COUPON_ID + ":" + USER_ID;
    private static final String RESULT_KEY = "coupon:result:" + COUPON_ID + ":" + USER_ID;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CouponUserMapper couponUserMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CouponMapper couponMapper;

    private CouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        UserContext.setUser(USER_ID);
        // ServiceImpl 的 baseMapper 为父类 protected 字段，构造注入无法覆盖，显式设置
        couponService = new CouponServiceImpl(stringRedisTemplate, couponUserMapper, objectMapper);
        ReflectionTestUtils.setField(couponService, "baseMapper", couponMapper);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("401：未登录领取 → UnauthorizedException（data=null）")
    void receiveCoupon_notLoggedIn_throwsUnauthorized() {
        UserContext.removeUser();

        assertThrows(UnauthorizedException.class, () -> couponService.receiveCoupon(COUPON_ID));
    }

    @Test
    @DisplayName("404：券不存在或已删除 → NotFoundException（RESOURCE_NOT_FOUND）")
    void receiveCoupon_couponMissing_throwsNotFound() {
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        assertThrows(NotFoundException.class, () -> couponService.receiveCoupon(COUPON_ID));
    }

    @Test
    @DisplayName("already_received：该用户已有券记录（含已使用/已过期/已软删）")
    void receiveCoupon_alreadyReceived_returnsAlreadyReceived() {
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(coupon(10));
        when(couponUserMapper.selectCount(any())).thenReturn(1L);

        CouponReceiveResultVO vo = couponService.receiveCoupon(COUPON_ID);

        assertEquals(CouponReceiveResultVO.RESULT_ALREADY_RECEIVED, vo.getResult());
        assertEquals("你已领过这张券", vo.getMessage());
        assertNull(vo.getCouponUser());
        verify(couponUserMapper, never()).insert(any(CouponUser.class));
    }

    @Test
    @DisplayName("频控：同用户同券 60s 内重复请求 → 400 领取过于频繁（不属四态，data=null）")
    void receiveCoupon_withinInterval_throwsRateLimit() {
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(coupon(10));
        when(couponUserMapper.selectCount(any())).thenReturn(0L);
        when(valueOperations.setIfAbsent(RECEIVE_LOCK_KEY, "1", 60L, TimeUnit.SECONDS)).thenReturn(false);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> couponService.receiveCoupon(COUPON_ID));

        assertEquals("领取过于频繁，请稍后再试", e.getMessage());
    }

    @Test
    @DisplayName("disabled：券已停用（status≠0）→ result=disabled / 该券已停用")
    void receiveCoupon_couponDisabled_returnsDisabled() {
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(coupon(10).setStatus(1));
        when(couponUserMapper.selectCount(any())).thenReturn(0L);
        stubLockPassed();

        CouponReceiveResultVO vo = couponService.receiveCoupon(COUPON_ID);

        assertEquals(CouponReceiveResultVO.RESULT_DISABLED, vo.getResult());
        assertEquals("该券已停用", vo.getMessage());
        verify(valueOperations, never()).decrement(STOCK_KEY);
    }

    @Test
    @DisplayName("disabled：不可主动领取的券（type≠0）同样按停用处理")
    void receiveCoupon_couponNotReceivable_returnsDisabled() {
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(coupon(10).setType(1));
        when(couponUserMapper.selectCount(any())).thenReturn(0L);
        stubLockPassed();

        CouponReceiveResultVO vo = couponService.receiveCoupon(COUPON_ID);

        assertEquals(CouponReceiveResultVO.RESULT_DISABLED, vo.getResult());
        assertEquals("该券已停用", vo.getMessage());
    }

    @Test
    @DisplayName("disabled：限时券不在有效期 → result=disabled / 该券不在有效期")
    void receiveCoupon_outOfTimeWindow_returnsDisabled() {
        Coupon expired = coupon(10)
                .setTimeType(1)
                .setStartTime(LocalDateTime.now().minusDays(3))
                .setEndTime(LocalDateTime.now().minusDays(1));
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(expired);
        when(couponUserMapper.selectCount(any())).thenReturn(0L);
        stubLockPassed();

        CouponReceiveResultVO vo = couponService.receiveCoupon(COUPON_ID);

        assertEquals(CouponReceiveResultVO.RESULT_DISABLED, vo.getResult());
        assertEquals("该券不在有效期", vo.getMessage());
    }

    @Test
    @DisplayName("sold_out：已发放数达到上限 → result=sold_out / 已领完（不得与已领过混用）")
    void receiveCoupon_issuedReachedTotal_returnsSoldOut() {
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(coupon(10));
        // 第一次：该用户记录数=0；第二次：该券已发放数=10
        when(couponUserMapper.selectCount(any())).thenReturn(0L, 10L);
        stubLockPassed();
        when(stringRedisTemplate.hasKey(STOCK_KEY)).thenReturn(true);

        CouponReceiveResultVO vo = couponService.receiveCoupon(COUPON_ID);

        assertEquals(CouponReceiveResultVO.RESULT_SOLD_OUT, vo.getResult());
        assertEquals("已领完", vo.getMessage());
        assertNull(vo.getCouponUser());
        verify(couponUserMapper, never()).insert(any(CouponUser.class));
    }

    @Test
    @DisplayName("sold_out：Redis 库存预减为负时按预减台账回补，并返回已领完")
    void receiveCoupon_stockPreDeductionNegative_rollsBackAndReturnsSoldOut() {
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(coupon(10));
        when(couponUserMapper.selectCount(any())).thenReturn(0L, 0L);
        stubLockPassed();
        when(stringRedisTemplate.hasKey(STOCK_KEY)).thenReturn(true);
        when(valueOperations.increment(RESERVED_KEY)).thenReturn(1L);
        when(valueOperations.decrement(STOCK_KEY)).thenReturn(-1L);
        when(valueOperations.decrement(RESERVED_KEY)).thenReturn(0L);

        CouponReceiveResultVO vo = couponService.receiveCoupon(COUPON_ID);

        assertEquals(CouponReceiveResultVO.RESULT_SOLD_OUT, vo.getResult());
        assertEquals("已领完", vo.getMessage());
        // 预减失败必须回补 1 份，否则 Redis 库存与 DB 永久漂移
        verify(valueOperations).increment(STOCK_KEY);
        verify(couponUserMapper, never()).insert(any(CouponUser.class));
    }

    @Test
    @DisplayName("success：同步落库出券，返回 result=success + couponUser，并回写 CI-55 轮询结果")
    void receiveCoupon_success_returnsSuccessWithCouponUser() {
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(coupon(10));
        // 用户记录数=0 → 已发放数=0 → 落库后已发放数=1
        when(couponUserMapper.selectCount(any())).thenReturn(0L, 0L, 1L);
        stubLockPassed();
        when(stringRedisTemplate.hasKey(STOCK_KEY)).thenReturn(true);
        when(valueOperations.increment(RESERVED_KEY)).thenReturn(1L);
        when(valueOperations.decrement(STOCK_KEY)).thenReturn(9L);
        when(couponUserMapper.insert(any(CouponUser.class))).thenAnswer(invocation -> {
            CouponUser cu = invocation.getArgument(0);
            cu.setId(900L);
            return 1;
        });

        CouponReceiveResultVO vo = couponService.receiveCoupon(COUPON_ID);

        assertEquals(CouponReceiveResultVO.RESULT_SUCCESS, vo.getResult());
        assertEquals("领取成功", vo.getMessage());
        assertNotNull(vo.getCouponUser());
        assertEquals(900L, vo.getCouponUser().getId());
        assertEquals(COUPON_ID, vo.getCouponUser().getCouponId());
        assertEquals("测试券", vo.getCouponUser().getName());
        // 成功路径不得回补库存，且必须写一份 CI-55 轮询结果
        verify(valueOperations, never()).increment(STOCK_KEY);
        verify(valueOperations).set(eq(RESULT_KEY), any(), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("并发撞键：DuplicateKeyException 收敛为 already_received，绝不冒泡 500")
    void receiveCoupon_duplicateKey_returnsAlreadyReceived() {
        when(couponMapper.selectOne(any(), anyBoolean())).thenReturn(coupon(10));
        when(couponUserMapper.selectCount(any())).thenReturn(0L, 0L);
        stubLockPassed();
        when(stringRedisTemplate.hasKey(STOCK_KEY)).thenReturn(true);
        when(valueOperations.increment(RESERVED_KEY)).thenReturn(1L);
        when(valueOperations.decrement(STOCK_KEY)).thenReturn(9L);
        when(valueOperations.decrement(RESERVED_KEY)).thenReturn(0L);
        when(couponUserMapper.insert(any(CouponUser.class)))
                .thenThrow(new DuplicateKeyException("uk_coupon_user"));

        CouponReceiveResultVO vo = couponService.receiveCoupon(COUPON_ID);

        assertEquals(CouponReceiveResultVO.RESULT_ALREADY_RECEIVED, vo.getResult());
        assertEquals("你已领过这张券", vo.getMessage());
        // 撞键即未出券：预减的 1 份库存必须回补
        verify(valueOperations).increment(STOCK_KEY);
    }

    // ---------------------------------------------------------------- helpers

    /** 券默认形态：正常在用、用户可主动领取、有限库存、按领取时间 days 天生效 */
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
                .setDays(30)
                .setDiscount(new BigDecimal("5.00"))
                .setMin(new BigDecimal("50.00"));
    }

    /** 防刷锁放行 */
    private void stubLockPassed() {
        when(valueOperations.setIfAbsent(RECEIVE_LOCK_KEY, "1", 60L, TimeUnit.SECONDS)).thenReturn(true);
    }
}
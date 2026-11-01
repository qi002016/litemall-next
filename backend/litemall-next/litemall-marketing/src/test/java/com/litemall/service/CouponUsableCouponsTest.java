package com.litemall.service;

import com.litemall.domain.PO.Coupon;
import com.litemall.domain.PO.CouponUser;
import com.litemall.domain.VO.CouponReceiveResultVO;
import com.litemall.domain.VO.UsableCouponVO;
import com.litemall.domain.VO.UsableCouponsVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.UnauthorizedException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>
 * CN-19 可用券试算（/app/market/coupon/usable）与「一人一券」回归测试。
 * </p>
 * <p>
 * 纯 Mockito，不加载 Spring 上下文：覆盖 08 §4.5 的可用性判定/不可用文案/排序/分换算，
 * 以及 08 §4.2 的「你已领过这张券」与「已领完」可区分（撞 uk_coupon_user 不得 500）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@ExtendWith(MockitoExtension.class)
class CouponUsableCouponsTest {

    private static final long USER_ID = 7L;

    private static final String RESERVED_KEY_PREFIX = "coupon:stock:reserved:";
    private static final String STOCK_KEY_PREFIX = "coupon:stock:";

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
        couponService = new CouponServiceImpl(stringRedisTemplate, couponUserMapper, producer, objectMapper);
        ReflectionTestUtils.setField(couponService, "baseMapper", couponMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    // ------------------------------------------------------------ CN-19 可用券

    @Test
    @DisplayName("CN-19：门槛已满足 → usable=true，面额/门槛/可抵扣金额均为分")
    void usableCoupons_usable() {
        when(couponUserMapper.selectList(any()))
                .thenReturn(List.of(couponUser(11L, 201L, (short) 0, LocalDateTime.now().minusDays(1))));
        when(couponMapper.selectByIds(any()))
                .thenReturn(List.of(coupon(201L, "满50减10", "10.00", "50.00")));

        UsableCouponsVO vo = couponService.getUsableCoupons(12800);

        assertEquals(12800, vo.getGoodsAmount());
        assertEquals(1, vo.getCoupons().size());
        UsableCouponVO item = vo.getCoupons().get(0);
        assertEquals(11L, item.getCouponUserId());
        assertEquals(201L, item.getCouponId());
        assertEquals("满50减10", item.getName());
        assertEquals(1000, item.getDiscount());
        assertEquals(5000, item.getMin());
        assertTrue(item.getUsable());
        assertNull(item.getUnusableReason());
        assertEquals(1000, item.getDeductAmount());
    }

    @Test
    @DisplayName("CN-19：门槛不足 → 文案「差 72.00 元可用」，deductAmount=0")
    void usableCoupons_belowMin() {
        when(couponUserMapper.selectList(any()))
                .thenReturn(List.of(couponUser(12L, 202L, (short) 0, LocalDateTime.now().minusDays(1))));
        when(couponMapper.selectByIds(any()))
                .thenReturn(List.of(coupon(202L, "满200减20", "20.00", "200.00")));

        UsableCouponVO item = couponService.getUsableCoupons(12800).getCoupons().get(0);

        assertFalse(item.getUsable());
        assertEquals("差 72.00 元可用", item.getUnusableReason());
        assertEquals(0, item.getDeductAmount());
    }

    @Test
    @DisplayName("CN-19：未到使用时间 → 文案「未到使用时间」")
    void usableCoupons_notStarted() {
        when(couponUserMapper.selectList(any()))
                .thenReturn(List.of(couponUser(13L, 203L, (short) 0, LocalDateTime.now().plusDays(2))));
        when(couponMapper.selectByIds(any()))
                .thenReturn(List.of(coupon(203L, "明日生效", "10.00", "0.00")));

        UsableCouponVO item = couponService.getUsableCoupons(12800).getCoupons().get(0);

        assertFalse(item.getUsable());
        assertEquals("未到使用时间", item.getUnusableReason());
        assertEquals(0, item.getDeductAmount());
    }

    @Test
    @DisplayName("CN-19：排序 = 可用在前 → 面额降序 → 到期时间升序")
    void usableCoupons_sorted() {
        LocalDateTime now = LocalDateTime.now();
        when(couponUserMapper.selectList(any())).thenReturn(List.of(
                couponUser(31L, 301L, (short) 0, now.minusDays(1)),
                couponUser(32L, 302L, (short) 0, now.minusDays(1)),
                couponUser(33L, 303L, (short) 0, now.minusDays(1))));
        when(couponMapper.selectByIds(any())).thenReturn(List.of(
                coupon(301L, "小额可用", "5.00", "0.00"),
                coupon(302L, "大额可用", "30.00", "0.00"),
                coupon(303L, "门槛不足", "50.00", "500.00")));

        List<UsableCouponVO> coupons = couponService.getUsableCoupons(12800).getCoupons();

        assertEquals(3, coupons.size());
        assertEquals(302L, coupons.get(0).getCouponId(), "可用券里面额大的排在前");
        assertEquals(301L, coupons.get(1).getCouponId());
        assertEquals(303L, coupons.get(2).getCouponId(), "不可用券排在最后");
    }

    @Test
    @DisplayName("CN-19：无可展示券 → coupons 为空数组而非 null")
    void usableCoupons_emptyList() {
        when(couponUserMapper.selectList(any())).thenReturn(List.of());

        UsableCouponsVO vo = couponService.getUsableCoupons(0);

        assertEquals(0, vo.getGoodsAmount());
        assertEquals(List.of(), vo.getCoupons());
    }

    @Test
    @DisplayName("CN-19：goodsAmount 缺失/负数为 400 PARAM_INVALID")
    void usableCoupons_paramInvalid() {
        assertThrows(BadRequestException.class, () -> couponService.getUsableCoupons(null));
        assertThrows(BadRequestException.class, () -> couponService.getUsableCoupons(-1));
    }

    @Test
    @DisplayName("CN-19：未登录 401")
    void usableCoupons_unauthorized() {
        UserContext.removeUser();

        assertThrows(UnauthorizedException.class, () -> couponService.getUsableCoupons(12800));
    }

    // ------------------------------------------------------- 一人一券（08 §4.2）

    @Test
    @DisplayName("一人一券：台账已有该券 → 直接提示「你已领过这张券」，不消耗任何 Redis 额度")
    void receiveCoupon_alreadyReceived_rejectsWithoutTouchRedis() {
        when(couponUserMapper.selectCount(any())).thenReturn(1L);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> couponService.receiveCoupon(401L));

        assertEquals("你已领过这张券", ex.getMessage());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("一人一券：落库撞 uk_coupon_user → 结果文案为「你已领过这张券」且仍回补库存")
    void persistReceive_duplicateKey_writesAlreadyReceived() {
        when(couponMapper.selectById(401L)).thenReturn(coupon(401L, "满50减10", "10.00", "50.00").setTotal(10));
        when(couponUserMapper.selectCount(any())).thenReturn(0L);
        // DB 原子扣 total 成功
        when(couponMapper.update(any(), any())).thenReturn(1);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // 本次确实预减过 1 份：台账消费后归零
        when(valueOperations.decrement(RESERVED_KEY_PREFIX + 401L + ":" + USER_ID)).thenReturn(0L);
        doThrow(new DuplicateKeyException("Duplicate entry for key 'uk_coupon_user'"))
                .when(couponUserMapper).insert(any(CouponUser.class));

        couponService.persistReceive(401L, USER_ID);

        verify(couponUserMapper).insert(any(CouponUser.class));
        ArgumentCaptor<CouponReceiveResultVO> captor = ArgumentCaptor.forClass(CouponReceiveResultVO.class);
        verify(objectMapper).writeValueAsString(captor.capture());
        assertEquals(CouponReceiveResultVO.FAILED, captor.getValue().getStatus());
        assertEquals("你已领过这张券", captor.getValue().getMessage());
        verify(valueOperations).increment(STOCK_KEY_PREFIX + 401L);
    }

    // ---------------------------------------------------------------- helpers

    private CouponUser couponUser(Long id, Long couponId, Short status, LocalDateTime startTime) {
        return new CouponUser()
                .setId(id)
                .setUserId(USER_ID)
                .setCouponId(couponId)
                .setStatus(status)
                .setStartTime(startTime)
                .setEndTime(LocalDateTime.now().plusDays(30))
                .setDeleted(false);
    }

    private Coupon coupon(Long id, String name, String discount, String min) {
        return new Coupon()
                .setId(id)
                .setName(name)
                .setTotal(100)
                .setLimit(1)
                .setStatus(0)
                .setType(0)
                .setDeleted(false)
                .setTimeType(0)
                .setDays(30)
                .setDiscount(new BigDecimal(discount))
                .setMin(new BigDecimal(min));
    }
}
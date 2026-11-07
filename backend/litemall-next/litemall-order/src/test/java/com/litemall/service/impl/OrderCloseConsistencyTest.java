package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderLog;
import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.domain.dto.OrderCloseDTO;
import com.litemall.exception.ConflictException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderStatsMapper;
import com.litemall.mq.OrderTimeoutPublisher;
import com.litemall.mapper.OrderMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BR-ORDER-13：三种关闭路径（CI-85 用户取消 / CN-10 运营关单 / CN-11 超时关单）
 * 必须共用同一套「CAS + 释放」语义。
 * <p>
 * 冻结口径：关闭统一写 {@code order_status=103 + close_type + end_time}；
 * 未支付订单从未实扣库存，故只回退优惠券，<b>不得</b>回补库存（否则会凭空涨库存）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrderCloseConsistencyTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private OrderGoodsMapper orderGoodsMapper;
    @Mock
    private AftersaleMapper aftersaleMapper;
    @Mock
    private OrderLogMapper orderLogMapper;

    @Mock
    private OrderStatsMapper orderStatsMapper;
    @Mock
    private GoodsClient goodsClient;
    @Mock
    private UserClient userClient;
    @Mock
    private MarketingClient marketingClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private OrderTimeoutPublisher orderTimeoutPublisher;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(cartMapper, orderGoodsMapper, aftersaleMapper, orderLogMapper, orderStatsMapper,
                goodsClient, userClient, marketingClient, stringRedisTemplate, orderTimeoutPublisher);
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "entityClass", Order.class);
        UserContext.setUser(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("CI-85 用户取消：close_type=1 + end_time，回退券但不回补库存")
    void userCancel_writesCloseType1() {
        when(orderMapper.selectOne(any())).thenReturn(unpaid(101L));
        stubCas(1);

        orderService.cancelOrder(101L);

        LambdaUpdateWrapper<Order> wrapper = captureCas();
        assertThat(wrapper.getParamNameValuePairs().values()).contains(103, 1);
        assertThat(endTimeSet(wrapper)).isTrue();
        verify(marketingClient).rollbackCoupon(any());
        verify(goodsClient, never()).releaseStockBatch(any());
        // CI-85 不写运营日志（C 端自助动作）
        verify(orderLogMapper, never()).insert(any(OrderLog.class));
    }

    @Test
    @DisplayName("CN-10 运营关单：close_type=3 + end_time，同样只回退券，并写运营日志")
    void adminClose_writesCloseType3() {
        when(orderMapper.selectOne(any())).thenReturn(unpaid(102L));
        stubCas(1);
        OrderCloseDTO dto = new OrderCloseDTO();
        dto.setOrderId(102L);
        dto.setAdminId(7L);

        orderService.adminClose(dto);

        LambdaUpdateWrapper<Order> wrapper = captureCas();
        assertThat(wrapper.getParamNameValuePairs().values()).contains(103, 3);
        assertThat(endTimeSet(wrapper)).isTrue();
        verify(marketingClient).rollbackCoupon(any());
        verify(goodsClient, never()).releaseStockBatch(any());
        ArgumentCaptor<OrderLog> logCaptor = ArgumentCaptor.forClass(OrderLog.class);
        verify(orderLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("order:close");
    }

    @Test
    @DisplayName("CN-11 超时关单：close_type=2 + end_time，释放语义与前两条完全一致")
    void timeoutClose_writesCloseType2() {
        when(orderMapper.selectList(any())).thenReturn(List.of(unpaid(103L)));
        stubCas(1);

        CloseExpiredResultDTO result = orderService.adminCloseExpired(100, false);

        assertThat(result.getSucceeded()).isEqualTo(1);
        LambdaUpdateWrapper<Order> wrapper = captureCas();
        assertThat(wrapper.getParamNameValuePairs().values()).contains(103, 2);
        assertThat(endTimeSet(wrapper)).isTrue();
        verify(marketingClient).rollbackCoupon(any());
        verify(goodsClient, never()).releaseStockBatch(any());
        ArgumentCaptor<OrderLog> logCaptor = ArgumentCaptor.forClass(OrderLog.class);
        verify(orderLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("order:close-expired");
    }

    @Test
    @DisplayName("三种路径：无券订单（couponPrice=0）不调 rollbackCoupon，避免无意义 RPC")
    void closeWithoutCoupon_skipsRollback() {
        Order noCoupon = unpaid(104L).setCouponPrice(BigDecimal.ZERO);
        when(orderMapper.selectOne(any())).thenReturn(noCoupon);
        stubCas(1);

        orderService.cancelOrder(104L);

        verify(marketingClient, never()).rollbackCoupon(any());
    }

    @Test
    @DisplayName("三条路径 CAS 失败（并发已关闭/已支付）一律 409 ORDER_STATUS_ILLEGAL")
    void allThreePaths_conflictWhenCasMissed() {
        when(orderMapper.selectOne(any())).thenReturn(unpaid(105L));
        stubCas(0);

        assertThatThrownBy(() -> orderService.cancelOrder(105L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("该订单当前状态不可取消");

        OrderCloseDTO dto = new OrderCloseDTO();
        dto.setOrderId(105L);
        assertThatThrownBy(() -> orderService.adminClose(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("该订单当前状态不可关闭");

        when(orderMapper.selectList(any())).thenReturn(List.of(unpaid(106L)));
        CloseExpiredResultDTO result = orderService.adminCloseExpired(100, false);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getItems().get(0).isOk()).isFalse();
    }

    @Test
    @DisplayName("释放失败不阻断关单：券回退异常只记日志，订单仍是关闭状态")
    void rollbackFailure_doesNotBreakClose() {
        when(orderMapper.selectOne(any())).thenReturn(unpaid(107L));
        stubCas(1);
        doThrow(new RuntimeException("marketing down")).when(marketingClient).rollbackCoupon(any());

        orderService.cancelOrder(107L);

        verify(marketingClient, times(1)).rollbackCoupon(any());
    }

    /* ==================== 辅助 ==================== */

    private void stubCas(int affected) {
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(affected);
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<Order> captureCas() {
        ArgumentCaptor<LambdaUpdateWrapper<Order>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(orderMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    /** end_time 是否进了 SET 子句：HALF_UP 口径下参数值里必有一个 LocalDateTime */
    private boolean endTimeSet(LambdaUpdateWrapper<Order> wrapper) {
        return wrapper.getParamNameValuePairs().values().stream()
                .anyMatch(v -> v instanceof LocalDateTime);
    }

    private Order unpaid(Long id) {
        return new Order()
                .setId(id)
                .setOrderSn("SN" + id)
                .setUserId(1L)
                .setOrderStatus(101)
                .setCouponPrice(new BigDecimal("5.00"))
                .setExpireTime(LocalDateTime.now().plusMinutes(10))
                .setDeleted(false);
    }
}
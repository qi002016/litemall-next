package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.constant.OrderStatus;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderLog;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderMapper;
import com.litemall.mapper.OrderStatsMapper;
import com.litemall.mq.OrderTimeoutPublisher;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R4-MQ：{@code closeExpiredOrder} 的幂等与容错语义（延时消息 / 兜底扫描共用同一关单实现）。
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutCloseTest {

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
    }

    private static Order unpaid(Long id, LocalDateTime expireTime) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(1L);
        order.setOrderStatus(OrderStatus.UNPAID);
        order.setCouponPrice(BigDecimal.ZERO);
        order.setExpireTime(expireTime);
        order.setDeleted(false);
        return order;
    }

    @Test
    @DisplayName("已到期未付款：CAS 关单成功，close_type=2 并留痕")
    void expired_unpaid_closed() {
        when(orderMapper.selectOne(any())).thenReturn(unpaid(21L, LocalDateTime.now().minusSeconds(5)));
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertThat(orderService.closeExpiredOrder(21L)).isTrue();

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).update(isNull(), captor.capture());
        assertThat(((LambdaUpdateWrapper<?>) captor.getValue()).getParamNameValuePairs().values())
                .contains(103, 2);
        verify(orderLogMapper).insert(org.mockito.ArgumentMatchers.<OrderLog>argThat(
                row -> "order:close-expired".equals(row.getAction())));
    }

    @Test
    @DisplayName("消息提前到达：不关单，但补投一条短延时消息自我纠偏（否则订单永不关闭）")
    void notExpired_republished() {
        Order early = unpaid(22L, LocalDateTime.now().plusSeconds(2));
        when(orderMapper.selectOne(any())).thenReturn(early);

        assertThat(orderService.closeExpiredOrder(22L)).isFalse();

        verify(orderMapper, never()).update(org.mockito.ArgumentMatchers.<Order>any(),
                org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<Order>>any());
        verify(orderTimeoutPublisher).publishAfterCommit(22L, early.getExpireTime());
    }

    @Test
    @DisplayName("订单已非未付款 / 不存在：返回 false，不视为失败")
    void missingOrNotUnpaid_skipped() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThat(orderService.closeExpiredOrder(23L)).isFalse();

        verify(orderMapper, never()).update(org.mockito.ArgumentMatchers.<Order>any(), org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<Order>>any());
        verify(orderLogMapper, never()).insert(org.mockito.ArgumentMatchers.<OrderLog>any());
    }

    @Test
    @DisplayName("并发被抢（CAS affected=0）：吞掉 409，返回 false，消息不重试")
    void concurrentClosed_returnsFalse() {
        when(orderMapper.selectOne(any())).thenReturn(unpaid(24L, LocalDateTime.now().minusSeconds(5)));
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThat(orderService.closeExpiredOrder(24L)).isFalse();

        verify(orderLogMapper, never()).insert(org.mockito.ArgumentMatchers.<OrderLog>any());
    }

    @Test
    @DisplayName("券回退失败：不阻断关单")
    void couponRollbackFailure_notBlocking() {
        Order order = unpaid(25L, LocalDateTime.now().minusSeconds(5));
        order.setCouponPrice(new BigDecimal("10.00"));
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        doThrow(new RuntimeException("marketing down")).when(marketingClient).rollbackCoupon(any());

        assertThat(orderService.closeExpiredOrder(25L)).isTrue();
    }

    @Test
    @DisplayName("orderId 为空：直接返回 false")
    void nullId_false() {
        assertThat(orderService.closeExpiredOrder(null)).isFalse();
        verify(orderMapper, never()).selectOne(org.mockito.ArgumentMatchers.<Wrapper<Order>>any());
    }
}
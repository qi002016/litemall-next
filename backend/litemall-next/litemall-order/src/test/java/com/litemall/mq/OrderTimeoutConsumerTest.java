package com.litemall.mq;

import com.litemall.service.IOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R4-MQ：消费端只做转发，幂等与关单语义全在 {@code IOrderService#closeExpiredOrder}。
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutConsumerTest {

    @Mock
    private IOrderService orderService;
    @InjectMocks
    private OrderTimeoutConsumer consumer;

    @Test
    @DisplayName("正常消息：转交 closeExpiredOrder")
    void onMessage_delegates() {
        when(orderService.closeExpiredOrder(11L)).thenReturn(true);

        consumer.onMessage(new OrderTimeoutMessage(11L, System.currentTimeMillis()));

        verify(orderService).closeExpiredOrder(11L);
    }

    @Test
    @DisplayName("消息体为空：丢弃，不触库")
    void onMessage_nullPayload_discarded() {
        consumer.onMessage(null);
        consumer.onMessage(new OrderTimeoutMessage());

        verify(orderService, never()).closeExpiredOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("服务抛异常：向外传播，交给 RocketMQ 重试直至死信")
    void onMessage_error_propagates() {
        when(orderService.closeExpiredOrder(12L)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> consumer.onMessage(new OrderTimeoutMessage(12L, 0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db down");
    }
}
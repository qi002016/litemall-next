package com.litemall.mq;

import com.litemall.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时关单消息消费端（R4-MQ）。
 *
 * <p><b>幂等</b>：关单判定全部交给 {@link IOrderService#closeExpiredOrder(Long)}——
 * 先校验 {@code order_status=101} 且 {@code now >= expire_time}，再走 CAS {@code 101 → 103}，
 * 因此同一条消息重复投递、或与兜底扫描同时命中同一单，都只会成功关一次。</p>
 *
 * <p><b>重试与死信</b>：只放行「业务上无需处理」的情况（返回 false），
 * 真正的异常（DB 不可用等）向上抛出，由 RocketMQ 按重试策略重投，超过上限进入死信队列，
 * 兜底扫描仍是最后一道闸门。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = OrderTimeoutPublisher.TOPIC, consumerGroup = "litemall-order-timeout-consumer")
public class OrderTimeoutConsumer implements RocketMQListener<OrderTimeoutMessage> {

    private final IOrderService orderService;

    @Override
    public void onMessage(OrderTimeoutMessage message) {
        if (message == null || message.getOrderId() == null) {
            log.warn("[MQ] 超时关单消息体为空，丢弃：{}", message);
            return;
        }
        boolean closed = orderService.closeExpiredOrder(message.getOrderId());
        log.info("[MQ] 超时关单消息消费完成：orderId={}, closed={}, expireAt={}",
                message.getOrderId(), closed, message.getExpireAt());
    }
}
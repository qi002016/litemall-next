package com.litemall.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 订单超时关单延时消息投递（R4-MQ）。
 *
 * <p>三条硬约束：</p>
 * <ol>
 *   <li><b>只在事务提交后投递</b>：{@code submitOrder} 带 {@code @GlobalTransactional}，若在事务体内发送，
 *       一旦后续分支出错回滚，就会出现「消息已发、订单不存在」的孤儿消息；这里统一注册
 *       {@link TransactionSynchronization#afterCommit()} 投递。</li>
 *   <li><b>投递失败不影响下单</b>：发送异常只记 ERROR，由兜底扫描
 *       （{@code OrderTimeoutFallbackScheduler}，走 CN-11）兜住，绝不把 MQ 抖动传染给用户下单链路。</li>
 *   <li><b>可关停</b>：{@code hm.order.timeout-mq.enabled=false} 时完全不碰 MQ，退化为纯扫描模式。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutPublisher {

    /** 延时消息 topic，与 {@link OrderTimeoutConsumer} 共用。 */
    public static final String TOPIC = "litemall-order-timeout";

    /** 发送超时（毫秒）。 */
    private static final long SEND_TIMEOUT_MS = 3000L;

    /**
     * RocketMQ 固定 18 级延时等级对应的秒数（4.x broker 与 5.x 客户端均支持）。
     * <p>broker 上被 {@code messageDelayLevel} 覆盖时以 broker 配置为准，这里只做「向上取整到最接近的等级」。</p>
     */
    private static final int[] DELAY_LEVEL_SECONDS = {
            1, 5, 10, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 1200, 1800, 3600, 7200
    };

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplate;

    @Value("${hm.order.timeout-mq.enabled:true}")
    private boolean enabled;

    /**
     * 事务提交后投递延时消息；无事务时立即投递。
     *
     * @param orderId    订单 ID
     * @param expireTime 支付截止时刻
     */
    public void publishAfterCommit(Long orderId, LocalDateTime expireTime) {
        if (!enabled || orderId == null || expireTime == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(orderId, expireTime);
                }
            });
        } else {
            send(orderId, expireTime);
        }
    }

    private void send(Long orderId, LocalDateTime expireTime) {
        RocketMQTemplate template = rocketMQTemplate.getIfAvailable();
        if (template == null) {
            log.warn("[MQ] 未装配 RocketMQTemplate，跳过延时消息（由兜底扫描关单）。orderId={}", orderId);
            return;
        }
        long remainSeconds = Duration.between(LocalDateTime.now(), expireTime).getSeconds();
        int delayLevel = delayLevelOf(remainSeconds);
        OrderTimeoutMessage message = new OrderTimeoutMessage(orderId,
                expireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        try {
            template.syncSend(TOPIC, MessageBuilder.withPayload(message).build(), SEND_TIMEOUT_MS, delayLevel);
            log.info("[MQ] 订单超时延时消息已投递：orderId={}, delayLevel={}, remainSeconds={}",
                    orderId, delayLevel, remainSeconds);
        } catch (Exception e) {
            log.error("[MQ] 订单超时延时消息投递失败，改由兜底扫描关单：orderId={}, delayLevel={}",
                    orderId, delayLevel, e);
        }
    }

    /** 取「不小于剩余秒数」的最小延时等级；超出最大等级时用最高级（2h）。 */
    static int delayLevelOf(long remainSeconds) {
        if (remainSeconds <= DELAY_LEVEL_SECONDS[0]) {
            return 1;
        }
        for (int i = 0; i < DELAY_LEVEL_SECONDS.length; i++) {
            if (remainSeconds <= DELAY_LEVEL_SECONDS[i]) {
                return i + 1;
            }
        }
        return DELAY_LEVEL_SECONDS.length;
    }
}
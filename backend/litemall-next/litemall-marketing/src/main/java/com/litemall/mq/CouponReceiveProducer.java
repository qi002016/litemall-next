package com.litemall.mq;

import com.litemall.domain.DTO.CouponReceiveMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponReceiveProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void send(Long couponId, Long userId) {
        CouponReceiveMessage msg = new CouponReceiveMessage(couponId, userId);
        rocketMQTemplate.syncSend(CouponReceiveMessage.TOPIC,
                MessageBuilder.withPayload(msg).build());
        log.info("领券消息已发送: couponId={}, userId={}", couponId, userId);
    }
}

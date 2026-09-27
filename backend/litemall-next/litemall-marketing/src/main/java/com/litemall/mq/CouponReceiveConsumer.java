package com.litemall.mq;

import com.litemall.domain.DTO.CouponReceiveMessage;
import com.litemall.service.ICouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = CouponReceiveMessage.TOPIC,
        consumerGroup = "litemall-marketing-consumer-group"
)
public class CouponReceiveConsumer implements RocketMQListener<CouponReceiveMessage> {

    private final ICouponService couponService;

    @Override
    public void onMessage(CouponReceiveMessage msg) {
        log.info("收到领券消息: {}", msg);
        couponService.persistReceive(msg.getCouponId(), msg.getUserId());
    }
}

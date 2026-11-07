package com.litemall.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单超时关单延时消息体（R4-MQ）。
 * <p>只承载定位信息，关单判定的唯一依据始终是库里的 {@code order_status} 与 {@code expire_time}
 * （见 {@code OrderServiceImpl#closeExpiredOrder}），因此消息重复投递不会造成重复关单。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage {

    /** 订单 ID。 */
    private Long orderId;

    /** 下单时写入的支付截止时刻（epoch 毫秒），仅用于排障对账，消费端以库为准。 */
    private Long expireAt;
}
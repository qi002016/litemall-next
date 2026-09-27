package com.litemall.domain.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponReceiveMessage implements Serializable {
    public static final String TOPIC = "coupon_receive_topic";
    private Long couponId;
    private Long userId;
}

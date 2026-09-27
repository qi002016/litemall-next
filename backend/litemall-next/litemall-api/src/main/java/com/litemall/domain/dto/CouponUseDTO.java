package com.litemall.domain.dto;

import lombok.Data;

/**
 * <p>
 * 优惠券核销/回退 DTO（Feign 跨服务调用：order → marketing）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
public class CouponUseDTO {

    /** 用户券记录ID（litemall_coupon_user.id） */
    private Long couponUserId;

    /** 关联订单ID（核销时必填） */
    private Long orderId;
}

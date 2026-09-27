package com.litemall.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * <p>
 * 优惠券抵扣试算 DTO（Feign 跨服务调用：order → marketing）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
public class CouponCheckDTO {

    /** 用户券记录ID（litemall_coupon_user.id） */
    private Long couponUserId;

    /** 商品总额 */
    private BigDecimal goodsAmount;
}

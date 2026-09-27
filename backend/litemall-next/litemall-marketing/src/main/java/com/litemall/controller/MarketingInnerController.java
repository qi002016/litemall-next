package com.litemall.controller;

import com.litemall.domain.dto.CouponCheckDTO;
import com.litemall.domain.dto.CouponUseDTO;
import com.litemall.service.ICouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * <p>
 * 营销服务内部接口（供其他服务 Feign 调用, 不走网关鉴权）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@RestController
@RequestMapping("/inner/market")
@RequiredArgsConstructor
@Tag(name = "营销内部接口", description = "供服务间 Feign 调用的营销接口")
public class MarketingInnerController {

    private final ICouponService couponService;

    @Operation(summary = "优惠券抵扣试算")
    @PostMapping("/coupon/check")
    public BigDecimal checkCoupon(@RequestBody CouponCheckDTO dto) {
        return couponService.checkCoupon(dto.getCouponUserId(), dto.getGoodsAmount());
    }

    @Operation(summary = "核销优惠券")
    @PostMapping("/coupon/use")
    public void useCoupon(@RequestBody CouponUseDTO dto) {
        couponService.useCoupon(dto.getCouponUserId(), dto.getOrderId());
    }

    @Operation(summary = "回退优惠券")
    @PostMapping("/coupon/rollback")
    public void rollbackCoupon(@RequestBody CouponUseDTO dto) {
        couponService.rollbackCoupon(dto.getCouponUserId(), dto.getOrderId());
    }
}

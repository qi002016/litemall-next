package com.litemall.client;

import com.litemall.domain.dto.CouponCheckDTO;
import com.litemall.domain.dto.CouponUseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

/**
 * <p>
 * 营销服务 Feign 客户端
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@FeignClient(name = "litemall-marketing")
public interface MarketingClient {

    /**
     * 优惠券抵扣试算（订单确认页 / 提交订单）
     *
     * @param dto couponUserId + goodsAmount
     * @return 可抵扣金额；券无效或未使用返回 0
     */
    @PostMapping("/inner/market/coupon/check")
    BigDecimal checkCoupon(@RequestBody CouponCheckDTO dto);

    /**
     * 核销优惠券（提交订单）
     *
     * @param dto couponUserId + orderId
     */
    @PostMapping("/inner/market/coupon/use")
    void useCoupon(@RequestBody CouponUseDTO dto);

    /**
     * 回退优惠券（取消订单）
     *
     * @param dto couponUserId
     */
    @PostMapping("/inner/market/coupon/rollback")
    void rollbackCoupon(@RequestBody CouponUseDTO dto);
}

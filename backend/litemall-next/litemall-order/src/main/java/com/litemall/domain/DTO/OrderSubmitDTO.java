package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 提交订单入参（CI-82，07 §2.2）。
 * <p>
 * {@code cartIds} 与 {@code buyNow} 二选一（必填性在服务内判定，见 CI-80 同款规则）；
 * {@code idemToken} 必填（P-02 保留原名），同键同参返回首次结果、同键不同参 → 409 IDEMPOTENT_CONFLICT。
 * </p>
 */
@Data
@Schema(description = "提交订单DTO")
public class OrderSubmitDTO {

    @Schema(description = "购物车项ID列表（与 buyNow 二选一，≤50 项）")
    private List<Long> cartIds;

    @Schema(description = "立即购买（与 cartIds 二选一）")
    private OrderConfirmDTO.BuyNow buyNow;

    @NotNull(message = "收货地址ID不能为空")
    @Schema(description = "收货地址ID")
    private Long addressId;

    @Schema(description = "优惠券ID（用户券记录ID，可选）")
    private Long couponId;

    @NotNull(message = "幂等token不能为空")
    @Schema(description = "一次性幂等token，由 /app/order/idem-token 申请")
    private String idemToken;

    @Schema(description = "订单留言（≤255）")
    private String message;
}
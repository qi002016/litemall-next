package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 订单确认页试算入参（CI-80，07 §2.1）。
 * <p>
 * <b>{@code cartIds} 与 {@code buyNow} 二选一</b>：都不传或都传 → 400 PARAM_INVALID。
 * 因此 {@code cartIds} 不再加 {@code @NotEmpty}，必填性在服务内按「二选一」判定。
 * </p>
 */
@Data
@Schema(description = "订单确认页DTO")
public class OrderConfirmDTO {

    @Schema(description = "购物车项ID列表（与 buyNow 二选一，≤50 项）")
    private List<Long> cartIds;

    @Schema(description = "立即购买（与 cartIds 二选一）")
    private BuyNow buyNow;

    @NotNull(message = "收货地址ID不能为空")
    @Schema(description = "收货地址ID")
    private Long addressId;

    @Schema(description = "优惠券ID（用户券记录ID，可选）")
    private Long couponId;

    @Data
    @Schema(description = "立即购买项")
    public static class BuyNow {

        @Schema(description = "货品/SKU ID（统一用 productId，不是 skuId）", type = "string")
        private Long productId;

        @Schema(description = "购买件数（≥1）")
        private Integer number;
    }
}
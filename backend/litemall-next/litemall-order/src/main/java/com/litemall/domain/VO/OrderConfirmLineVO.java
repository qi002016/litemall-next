package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单确认页行（CI-80 {@code lines[]}，07 §2.1）。
 * <p>金额一律 <b>int 分</b>；{@code availableStock} 只做提示，<b>不阻断</b>试算。</p>
 */
@Data
@Schema(description = "订单确认行")
public class OrderConfirmLineVO {

    @Schema(description = "货品/SKU ID", type = "string")
    private Long productId;

    @Schema(description = "商品名")
    private String goodsName;

    @Schema(description = "规格文案，如「黑色 / L」")
    private String specText;

    @Schema(description = "成交单价（分）")
    private Integer price;

    @Schema(description = "数量")
    private Integer number;

    @Schema(description = "行商品金额（分）")
    private Integer lineAmount;

    @Schema(description = "行券分摊（分）")
    private Integer couponShare;

    @Schema(description = "行实付（分）= lineAmount - couponShare")
    private Integer actualAmount;

    @Schema(description = "当前可售件数（提示用，可售不足不阻断）")
    private Integer availableStock;
}
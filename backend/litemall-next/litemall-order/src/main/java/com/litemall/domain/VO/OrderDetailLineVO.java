package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单详情行（CI-84 {@code lines[]}，07 §2.4）。金额一律 <b>int 分</b>。
 * <p>
 * {@code canApply} 每次实时计算、不缓存；{@code types} 只取 1/2（0 是历史存量不返），
 * 空数组表示不可申请，此时 {@code maxQty=0} 且 {@code reason} 给出原因。
 * </p>
 */
@Data
@Schema(description = "订单详情行")
public class OrderDetailLineVO {

    @Schema(description = "订单行ID", type = "string")
    private Long orderGoodsId;

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

    @Schema(description = "行实付（分）")
    private Integer actualAmount;

    @Schema(description = "行券分摊（分）")
    private Integer couponShare;

    @Schema(description = "已退件数")
    private Integer refundedNumber;

    @Schema(description = "行退款状态 0 未退款 / 1 部分退款 / 2 全额退款")
    private Integer refundStatus;

    @Schema(description = "行退款状态文案")
    private String refundStatusText;

    @Schema(description = "售后可申请信息")
    private CanApply canApply;

    @Data
    @Schema(description = "售后可申请信息")
    public static class CanApply {

        @Schema(description = "可申请的售后类型：1 仅退款 / 2 退货退款；空数组=不可申请")
        private java.util.List<Integer> types;

        @Schema(description = "本次最多可申请件数（不可申请为 0）")
        private Integer maxQty;

        @Schema(description = "不可申请原因；可申请为 null")
        private String reason;
    }
}
package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 售后列表行（CN-15 / 07 §4.2）。金额为整数分。
 */
@Data
@Schema(description = "售后列表行")
public class AftersaleListItemVO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "售后编号")
    private String aftersaleSn;

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "订单行ID", type = "string")
    private Long orderGoodsId;

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "商品图片")
    private String picUrl;

    @Schema(description = "规格文案")
    private String specText;

    @Schema(description = "申请退款数量")
    private Integer number;

    @Schema(description = "售后类型：1 仅退款 / 2 退货退款")
    private Integer type;

    @Schema(description = "售后类型文案")
    private String typeText;

    @Schema(description = "售后状态标识：applied/waitReturn/waitReceive/refunding/completed/closed/rejected/cancelled")
    private String status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "退款金额（整数分）")
    private Integer amount;

    @Schema(description = "回补状态：0 未回补 / 1 已回补 / 2 待回补")
    private Integer restockStatus;

    @Schema(description = "回补状态文案")
    private String restockStatusText;

    @Schema(description = "退货物流单号")
    private String returnShipSn;

    @Schema(description = "待寄回截止时间（yyyy-MM-dd HH:mm:ss）")
    private String returnDeadline;

    @Schema(description = "申请时间（yyyy-MM-dd HH:mm:ss）")
    private String applyTime;
}

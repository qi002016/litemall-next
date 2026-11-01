package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 申请售后结果（CI-91 / 07 §4.1 出参）。
 */
@Data
@Schema(description = "申请售后结果VO")
public class AftersaleApplyVO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "售后编号")
    private String aftersaleSn;

    @Schema(description = "售后状态标识：applied")
    private String status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "售后类型：1 仅退款 / 2 退货退款")
    private Integer type;

    @Schema(description = "售后类型文案")
    private String typeText;

    @Schema(description = "退款金额（整数分）")
    private Integer amount;

    @Schema(description = "申请退款数量")
    private Integer number;

    @Schema(description = "订单行ID", type = "string")
    private Long orderGoodsId;

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "申请时间（yyyy-MM-dd HH:mm:ss）")
    private String applyTime;
}
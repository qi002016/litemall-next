package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单售后摘要（CI-84 {@code aftersale}，最近一笔非终态售后；07 §2.4）。
 */
@Data
@Schema(description = "订单售后摘要")
public class OrderAftersaleBriefVO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "售后单号")
    private String aftersaleSn;

    @Schema(description = "售后状态码（0 申请中 / 1 待寄回 / 2 待收货 / 3 退款中 / 4 完成 / 5 关闭 / 6 驳回 / 7 撤销）")
    private Integer status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "售后类型 1 仅退款 / 2 退货退款")
    private Integer type;

    @Schema(description = "售后类型文案")
    private String typeText;
}
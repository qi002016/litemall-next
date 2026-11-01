package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 订单确认试算VO（CI-80，07 §2.1）。金额一律 <b>int 分</b>。
 */
@Data
@Schema(description = "订单确认试算VO")
public class OrderConfirmVO {

    @Schema(description = "商品总额（分）")
    private Integer goodsAmount;

    @Schema(description = "运费（分，当前恒 0）")
    private Integer freightAmount;

    @Schema(description = "优惠券抵扣（分）")
    private Integer couponAmount;

    @Schema(description = "应付总额（分）")
    private Integer orderAmount;

    @Schema(description = "行明细（含券额分摊与可售提示）")
    private List<OrderConfirmLineVO> lines;
}
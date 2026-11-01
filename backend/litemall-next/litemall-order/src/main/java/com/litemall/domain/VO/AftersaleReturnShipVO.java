package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户填写退货物流结果（CN-18 出参）。
 */
@Data
@Schema(description = "售后寄回结果VO")
public class AftersaleReturnShipVO {

    @Schema(description = "售后状态标识：waitReceive")
    private String status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "填单号时间（yyyy-MM-dd HH:mm:ss）")
    private String returnShipTime;
}

package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 售后退款结果DTO（CN-IN-02 出参）
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后退款结果")
public class PayRefundResultDTO {

    @Schema(description = "退款单ID", type = "string")
    private Long refundId;

    @Schema(description = "退款状态：1 退款成功")
    private Integer status;

    @Schema(description = "退款状态文案")
    private String statusText;

    @Schema(description = "退款完成时间（yyyy-MM-dd HH:mm:ss）")
    private String refundTime;
}
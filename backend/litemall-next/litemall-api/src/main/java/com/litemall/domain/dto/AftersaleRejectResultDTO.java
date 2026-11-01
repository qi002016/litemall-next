package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 售后驳回出参（CN-04）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后驳回出参")
public class AftersaleRejectResultDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "售后状态标识")
    private String status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "驳回原因")
    private String reason;
}

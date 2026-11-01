package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 售后关闭出参（CN-09）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后关闭出参")
public class AftersaleCloseResultDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "售后状态标识")
    private String status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "关闭原因")
    private String closeReason;
}

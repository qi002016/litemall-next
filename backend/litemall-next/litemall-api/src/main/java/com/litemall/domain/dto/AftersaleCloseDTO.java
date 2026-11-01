package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端关闭售后入参（CN-09）。{@code reason} 必填并写入 {@code close_reason}。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后关闭入参")
public class AftersaleCloseDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "关闭原因")
    private String reason;

    @Schema(description = "操作人 admin.id", type = "string")
    private Long adminId;
}

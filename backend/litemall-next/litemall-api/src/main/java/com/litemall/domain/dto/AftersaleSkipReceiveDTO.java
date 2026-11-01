package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端跳过收货入参（CN-06）。{@code reason} 必填并写操作日志（BR-AS-09）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后跳过收货入参")
public class AftersaleSkipReceiveDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "跳过收货原因")
    private String reason;

    @Schema(description = "操作人 admin.id", type = "string")
    private Long adminId;
}

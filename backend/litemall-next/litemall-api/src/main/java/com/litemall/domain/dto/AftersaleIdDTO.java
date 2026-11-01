package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 只带售后单 ID 的动作入参（CN-05 确认收货、CN-08 重试回补）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后单动作入参")
public class AftersaleIdDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "操作人 admin.id", type = "string")
    private Long adminId;
}

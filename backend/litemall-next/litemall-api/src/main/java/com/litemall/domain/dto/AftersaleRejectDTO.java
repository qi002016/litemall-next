package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端售后驳回入参（CN-04）。{@code reason} 必填且 C 端详情可见（BR-AS-22）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后驳回入参")
public class AftersaleRejectDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "驳回原因")
    private String reason;

    @Schema(description = "操作人 admin.id", type = "string")
    private Long adminId;
}

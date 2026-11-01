package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端售后单只带 ID 的请求（CN-05 确认收货 {@code /admin/aftersale/confirm-receive}、
 * CN-08 重试回补 {@code /admin/aftersale/retry-restock}）。
 */
@Data
@Schema(description = "售后单ID请求")
public class AftersaleIdReqDTO {

    @NotNull(message = "售后单ID不能为空")
    @Schema(description = "售后单ID")
    private Long aftersaleId;
}

package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * <p>
 * C 端撤销售后申请入参（CN-17，07 §4.3）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "撤销售后申请入参")
public class AftersaleCancelDTO {

    @Schema(description = "售后单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "售后单ID不能为空")
    private Long aftersaleId;
}
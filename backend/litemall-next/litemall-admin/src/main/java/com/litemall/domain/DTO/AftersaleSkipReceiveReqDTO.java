package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端跳过收货请求（CN-06 {@code POST /admin/aftersale/skip-receive}）。
 * <p>
 * 允许状态 1（待寄回）与 2（待收货）；{@code reason} 必填，跳过后直接进入退款中（3）。
 * </p>
 */
@Data
@Schema(description = "管理端跳过收货请求")
public class AftersaleSkipReceiveReqDTO {

    @NotNull(message = "售后单ID不能为空")
    @Schema(description = "售后单ID（须为待寄回 waitReturn 或待收货 waitReceive）")
    private Long aftersaleId;

    @NotBlank(message = "跳过收货原因不能为空")
    @Size(max = 255, message = "跳过收货原因长度不能超过 255 位")
    @Schema(description = "跳过收货原因（必填，≤255）")
    private String reason;
}

package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端关闭售后单请求（CN-09 {@code POST /admin/aftersale/close}）。
 * <p>
 * 允许状态 0/1/2；3（退款中）与终态禁止关闭。{@code reason} 必填。
 * </p>
 */
@Data
@Schema(description = "管理端关闭售后单请求")
public class AftersaleCloseReqDTO {

    @NotNull(message = "售后单ID不能为空")
    @Schema(description = "售后单ID（须为申请中/待寄回/待收货）")
    private Long aftersaleId;

    @NotBlank(message = "关闭原因不能为空")
    @Size(max = 255, message = "关闭原因长度不能超过 255 位")
    @Schema(description = "关闭原因（必填，≤255）")
    private String reason;
}

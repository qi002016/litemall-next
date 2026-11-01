package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端售后驳回请求（CN-04 {@code POST /admin/aftersale/reject}）。
 * <p>
 * {@code reason} 必填（≤255、非空白），会作为驳回原因返回给用户。
 * </p>
 */
@Data
@Schema(description = "管理端售后驳回请求")
public class AftersaleRejectReqDTO {

    @NotNull(message = "售后单ID不能为空")
    @Schema(description = "售后单ID（须为申请中 applied）")
    private Long aftersaleId;

    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 255, message = "驳回原因长度不能超过 255 位")
    @Schema(description = "驳回原因（必填，≤255）")
    private String reason;
}

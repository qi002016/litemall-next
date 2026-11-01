package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端立即关单请求（CN-10 {@code POST /admin/order/close}）。
 * <p>
 * {@code reason} 需求未定义为必填，保持可空（≤255），不得擅自加必填。
 * </p>
 */
@Data
@Schema(description = "管理端关单请求")
public class OrderCloseReqDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @Size(max = 255, message = "关单原因长度不能超过 255 位")
    @Schema(description = "关单原因（可空，≤255）")
    private String reason;
}
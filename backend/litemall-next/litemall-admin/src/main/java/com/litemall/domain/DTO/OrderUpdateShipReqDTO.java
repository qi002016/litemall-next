package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端修改物流单号请求（CN-12 {@code POST /admin/order/update-ship}）。
 * <p>
 * P-21：仅 201（已发货）且此前未改过（{@code litemall_log} 计数）可改；状态与次数判定在 order 侧，
 * admin 只校验字段（快递公司与单号均必填、≤64、非空白）。
 * </p>
 */
@Data
@Schema(description = "管理端修改物流请求")
public class OrderUpdateShipReqDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotBlank(message = "快递公司不能为空")
    @Size(max = 64, message = "快递公司长度不能超过 64 位")
    @Schema(description = "快递公司（必填，≤64）")
    private String shipChannel;

    @NotBlank(message = "快递单号不能为空")
    @Size(max = 64, message = "快递单号长度不能超过 64 位")
    @Schema(description = "快递单号（必填，≤64）")
    private String shipSn;

    @Size(max = 255, message = "修改原因长度不能超过 255 位")
    @Schema(description = "修改原因（可空，≤255）")
    private String reason;
}
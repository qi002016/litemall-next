package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 发货 / 修改物流请求 DTO（CI-101 {@code /inner/order/admin/ship}、CN-12 {@code .../update-ship}）。
 * </p>
 * <p>
 * P-18：字段不变，只补「非空、≤64、非空白」校验（在 order 服务内校验，避免 shared 模块引入
 * validation 依赖）；{@code reason} 仅 CN-12 使用（≤255，可空）。
 * </p>
 */
@Data
@Schema(description = "发货/修改物流请求")
public class OrderShipDTO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "快递公司（必填，≤64）")
    private String shipChannel;

    @Schema(description = "快递单号（必填，≤64）")
    private String shipSn;

    @Schema(description = "修改物流原因（CN-12 可空，≤255）")
    private String reason;
}
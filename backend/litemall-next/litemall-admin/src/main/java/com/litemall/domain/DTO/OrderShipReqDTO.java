package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * M13：管理端发货请求（原为 {@code Map<String, Object>} + 手工 toLong/toStr 解析）。
 */
@Data
@Schema(description = "管理端发货请求")
public class OrderShipReqDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @Size(max = 64, message = "快递公司长度不能超过 64 位")
    @Schema(description = "快递公司（可空）")
    private String shipChannel;

    @Size(max = 64, message = "快递单号长度不能超过 64 位")
    @Schema(description = "快递单号（可空）")
    private String shipSn;
}
package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端发货请求（CI-27 {@code POST /admin/order/ship}）。
 * <p>
 * P-18：字段与改造前一致，仅把 {@code shipChannel}/{@code shipSn} 的「非空、≤64、非空白」
 * 校验前移到 admin 入口（order 侧仍会再校验一次）。
 * </p>
 */
@Data
@Schema(description = "管理端发货请求")
public class OrderShipReqDTO {

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
}
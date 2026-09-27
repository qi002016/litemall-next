package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端发货请求 DTO（H5：admin 不再直写 litemall_order，改经 order 服务内状态机）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端发货请求")
public class OrderShipDTO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "快递公司（可空）")
    private String shipChannel;

    @Schema(description = "快递单号（可空）")
    private String shipSn;
}
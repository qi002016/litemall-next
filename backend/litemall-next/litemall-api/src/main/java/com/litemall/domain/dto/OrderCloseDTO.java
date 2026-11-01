package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 运营关单请求（CN-10 {@code /inner/order/admin/close}；admin 侧 {@code POST /admin/order/close}）。
 * </p>
 * <p>
 * {@code adminId} 由 admin 服务从登录上下文显式透传（P-19）；order 侧「透传优先、缺失记 null」，
 * <b>禁止伪造 0</b>（否则日志会把系统操作算成某个管理员）。
 * </p>
 */
@Data
@Schema(description = "运营关单请求")
public class OrderCloseDTO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "关单原因（可空，≤255）")
    private String reason;

    @Schema(description = "操作管理员ID（可空；缺失记 null）", type = "string")
    private Long adminId;
}
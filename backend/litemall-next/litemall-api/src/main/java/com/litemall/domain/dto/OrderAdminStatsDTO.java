package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端首页订单统计 DTO（admin 服务的 /admin/dashboard/stats 用，H5）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端订单统计")
public class OrderAdminStatsDTO {

    @Schema(description = "今日订单数")
    private Long todayOrders;

    @Schema(description = "待发货订单数（order_status=102）")
    private Long undelivered;
}
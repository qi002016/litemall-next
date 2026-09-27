package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * M13：管理端首页统计响应（原为 {@code Map<String, Object>}）。
 * <p>
 * 字段名与改造前逐字保持一致（goodsTotal/userTotal/todayOrders/undelivered），
 * 故管理端前端无需改动。四个计数都是 {@code Long}：原 Map 里 order 侧为 Long、
 * goods/user 侧取决于 inner 接口返回值，统一成 Long 避免前端出现 0 与 0L 两种形态。
 */
@Data
@Schema(description = "管理端首页统计")
public class DashboardStatsVO {

    @Schema(description = "商品总数")
    private Long goodsTotal;

    @Schema(description = "用户总数")
    private Long userTotal;

    @Schema(description = "今日订单数")
    private Long todayOrders;

    @Schema(description = "待发货订单数")
    private Long undelivered;
}
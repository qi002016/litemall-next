package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.client.OrderClient;
import com.litemall.client.UserClient;
import com.litemall.domain.Result;
import com.litemall.domain.VO.DashboardStatsVO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 管理端首页统计。
 * </p>
 * <p>
 * H5：不再直连 {@code litemall_goods/litemall_order/litemall_user} 三张表，
 * 改为分别经 {@link GoodsClient}/{@link OrderClient}/{@link UserClient}
 * 调用各域的 {@code /inner/**} 统计接口；返回字段名与改造前一致。
 * </p>
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final GoodsClient goodsClient;
    private final OrderClient orderClient;
    private final UserClient userClient;

    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats() {
        OrderAdminStatsDTO orderStats = orderClient.adminOrderStats();
        DashboardStatsVO stats = new DashboardStatsVO();
        stats.setGoodsTotal(goodsClient.adminCountGoods());
        stats.setUserTotal(userClient.adminCountUsers());
        stats.setTodayOrders(orderStats == null ? 0L : orderStats.getTodayOrders());
        stats.setUndelivered(orderStats == null ? 0L : orderStats.getUndelivered());
        return Result.ok(stats);
    }
}
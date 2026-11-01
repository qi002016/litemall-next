package com.litemall.controller;

import com.litemall.domain.Result;
import com.litemall.domain.VO.DashboardStatsVO;
import com.litemall.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 管理端工作台报表（CI-08）。
 * </p>
 * <p>
 * 端点契约见 05-R2 实施口径 §2.1：{@code GET /admin/dashboard/stats?period=today|7d|30d}；
 * {@code period} 缺省或非枚举值一律按 {@code 30d}（不报错，§2.1.1）。
 * 数字装配与韧性兜底全部在 {@link DashboardService}，控制器只做透传。
 * </p>
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "管理端-工作台")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "工作台报表（CI-08）：五个数字 + Top5 + 趋势",
            description = "period=today|7d|30d，缺省 30d；非枚举值按 30d 处理不报错。"
                    + "单指标失败时该字段为 null 并在 unavailable 中登记，接口仍 200。")
    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats(
            @Parameter(description = "统计期：today/7d/30d，缺省 30d")
            @RequestParam(value = "period", required = false) String period) {
        return Result.ok(dashboardService.stats(period));
    }
}
package com.litemall.service;

import com.litemall.client.GoodsClient;
import com.litemall.client.OrderClient;
import com.litemall.client.UserClient;
import com.litemall.domain.VO.DashboardStatsVO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.support.LogErrorCounter;
import com.litemall.support.StatsPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 管理端工作台报表（CI-08，05-R2 实施口径 §2.1）。
 * </p>
 * <p>
 * 职责分层：
 * </p>
 * <ul>
 *   <li><b>订单域数字</b>（gmv/orderCount/refundAmount/refundCount/top5/trend/todayOrders/undelivered/
 *       waitShipCount/pendingAftersaleCount）由 {@link OrderClient#adminOrderStats(String)}（CI-100）一次取全，
 *       SQL 口径在 order 域冻结（§2.1.3 SQL-1..SQL-5），本类<b>不重算</b>，只做单位与派生；</li>
 *   <li><b>派生数字</b>（avgOrderValue/refundRate）在本类计算，规则见 §2.1.2；</li>
 *   <li><b>商品/用户总数</b>走既有 Feign（{@code /inner/<domain>/admin/count}），不新增跨库直连（§1.7）；</li>
 *   <li><b>日志异常计数</b>读 Redis {@code admin:logerr:{yyyyMMdd}}（P-3）。</li>
 * </ul>
 * <p>
 * 韧性（§2.1.4）：任一指标失败只把该指标置 {@code null} 并登记到 {@code unavailable}，
 * <b>接口整体仍 200</b>，绝不 500 —— 工作台是首页，不能因为一个子查询拖垮整页。
 * </p>
 *
 * @since 2026-11-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 单指标失败白名单（§2.1.2 冻结字面量，顺序即写入 unavailable 的顺序） */
    static final List<String> ORDER_METRICS = List.of(
            "gmv", "refundAmount", "waitShipCount", "pendingAftersaleCount", "top5", "trend");

    private final GoodsClient goodsClient;
    private final OrderClient orderClient;
    private final UserClient userClient;
    private final LogErrorCounter logErrorCounter;

    /**
     * 工作台报表。
     *
     * @param periodRaw 统计期 {@code today|7d|30d}；缺省/非枚举值一律按 30d（不报错，§2.1.1）
     * @return 报表；任何单指标失败都不抛异常
     */
    public DashboardStatsVO stats(String periodRaw) {
        StatsPeriod window = StatsPeriod.resolve(periodRaw);

        DashboardStatsVO vo = new DashboardStatsVO();
        vo.setPeriod(window.code());
        vo.setPeriodStart(window.startText());
        vo.setPeriodEnd(window.endText());
        List<String> unavailable = new ArrayList<>();

        assembleOrderMetrics(vo, window, unavailable);

        // 派生：客单价与退款率必须基于「同一批」原子数字计算，避免各自取数导致口径漂移
        vo.setAvgOrderValue(avgOrderValue(vo.getGmv(), vo.getOrderCount()));
        vo.setRefundRate(refundRate(vo.getRefundCount(), vo.getOrderCount()));

        vo.setGoodsTotal(bestEffort("goodsTotal", goodsClient::adminCountGoods));
        vo.setUserTotal(bestEffort("userTotal", userClient::adminCountUsers));
        vo.setLogErrorCount(logErrorCounter.todayCount());

        vo.setUnavailable(List.copyOf(unavailable));
        return vo;
    }

    /**
     * 订单域指标装配；整块失败时把 6 个白名单指标全部登记为不可用。
     */
    private void assembleOrderMetrics(DashboardStatsVO vo, StatsPeriod window, List<String> unavailable) {
        // 先给前端合法的空数组/补零序列：数组类字段即使取数失败也不返回 null（避免前端 undefined）
        vo.setTop5(List.of());
        vo.setTrend(fillTrend(window, null));
        OrderAdminStatsDTO stats;
        try {
            stats = orderClient.adminOrderStats(window.code());
        } catch (Exception e) {
            log.warn("CI-08 订单统计不可用（period={}）：{}", window.code(), e.toString());
            stats = null;
        }
        if (stats == null) {
            unavailable.addAll(ORDER_METRICS);
            return;
        }
        vo.setGmv(nz(stats.getGmv()));
        vo.setOrderCount(nullSafe(stats.getOrderCount()));
        vo.setRefundCount(nullSafe(stats.getRefundCount()));
        vo.setRefundAmount(nz(stats.getRefundAmount()));
        vo.setWaitShipCount(nullSafe(stats.getWaitShipCount()));
        vo.setPendingAftersaleCount(nullSafe(stats.getPendingAftersaleCount()));
        vo.setTodayOrders(nullSafe(stats.getTodayOrders()));
        // undelivered 与 waitShipCount 同值同源（§2.1.2：均=当前 order_status=102 存量）
        vo.setUndelivered(nullSafe(stats.getUndelivered() != null
                ? stats.getUndelivered() : stats.getWaitShipCount()));
        vo.setTop5(stats.getTop5() == null ? List.of() : stats.getTop5());
        vo.setTrend(fillTrend(window, stats.getTrend()));
    }

    /**
     * 客单价 = gmv ÷ orderCount，向下取整到「分」。
     *
     * @return 整数分；{@code orderCount} 为 0/未知时 {@code null}（前端显示 {@code —}，不显示 0/NaN）
     */
    static BigDecimal avgOrderValue(BigDecimal gmv, Long orderCount) {
        if (gmv == null || orderCount == null || orderCount <= 0L) {
            return null;
        }
        return gmv.divide(BigDecimal.valueOf(orderCount), 0, RoundingMode.DOWN);
    }

    /**
     * 退款率 = refundCount ÷ orderCount，保留 4 位小数（HALF_UP）。
     *
     * @return 比例值；分母为 0/未知时 {@code null}
     */
    static BigDecimal refundRate(Long refundCount, Long orderCount) {
        if (refundCount == null || orderCount == null || orderCount <= 0L) {
            return null;
        }
        return BigDecimal.valueOf(refundCount)
                .divide(BigDecimal.valueOf(orderCount), 4, RoundingMode.HALF_UP);
    }

    /**
     * 把订单域返回的趋势点铺满整段自然日（缺失日补 0），长度恒为 {@code window.days()}。
     */
    static List<OrderAdminStatsDTO.Trend> fillTrend(StatsPeriod window,
                                                    List<OrderAdminStatsDTO.Trend> source) {
        Map<String, OrderAdminStatsDTO.Trend> byDate = new LinkedHashMap<>();
        if (source != null) {
            for (OrderAdminStatsDTO.Trend point : source) {
                if (point != null && point.getDate() != null) {
                    byDate.put(point.getDate(), point);
                }
            }
        }
        List<OrderAdminStatsDTO.Trend> result = new ArrayList<>(window.days());
        for (String date : window.dates()) {
            OrderAdminStatsDTO.Trend hit = byDate.get(date);
            OrderAdminStatsDTO.Trend point = new OrderAdminStatsDTO.Trend();
            point.setDate(date);
            point.setOrderCount(hit == null ? 0L : nullSafe(hit.getOrderCount()));
            point.setGmv(hit == null ? BigDecimal.ZERO : nz(hit.getGmv()));
            result.add(point);
        }
        return result;
    }

    /**
     * 计数类单指标：失败只 WARN 并返回 {@code null}，绝不打断整页。
     */
    private Long bestEffort(String metric, CountSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("CI-08 指标 {} 不可用：{}", metric, e.toString());
            return null;
        }
    }

    /** 同值保护：null → 0（金额字段空集合按 0 分展示，BR-RPT-08） */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 同值保护：null → 0（计数缺省 0） */
    private static Long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    /** 允许抛异常的计数取数（便于统一兜底） */
    @FunctionalInterface
    interface CountSupplier {
        Long get() throws Exception;
    }
}
package com.litemall.service;

import com.litemall.support.StatsPeriod;

import com.litemall.client.GoodsClient;
import com.litemall.client.OrderClient;
import com.litemall.client.UserClient;
import com.litemall.domain.VO.DashboardStatsVO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.support.LogErrorCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2（CI-08）：工作台报表装配单测（05-R2 实施口径 §2.1）。
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private GoodsClient goodsClient;
    @Mock
    private OrderClient orderClient;
    @Mock
    private UserClient userClient;
    @Mock
    private LogErrorCounter logErrorCounter;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(goodsClient, orderClient, userClient, logErrorCounter);
    }

    @Test
    @DisplayName("五个数字：gmv/orderCount 取订单域，客单价与退款率按 §2.1.2 派生")
    void fiveNumbersDerived() {
        when(orderClient.adminOrderStats("7d")).thenReturn(stats(new BigDecimal("128800"), 12L, 1L,
                new BigDecimal("12900")));
        when(goodsClient.adminCountGoods()).thenReturn(215L);
        when(userClient.adminCountUsers()).thenReturn(48L);
        when(logErrorCounter.todayCount()).thenReturn(2L);

        DashboardStatsVO vo = service.stats("7d");

        assertThat(vo.getPeriod()).isEqualTo("7d");
        assertThat(vo.getGmv()).isEqualByComparingTo("128800");
        assertThat(vo.getOrderCount()).isEqualTo(12L);
        // 128800 ÷ 12 = 10733.33…，向下取整到分 = 10733
        assertThat(vo.getAvgOrderValue()).isEqualByComparingTo("10733");
        assertThat(vo.getAvgOrderValue().scale()).isZero();
        // 1 ÷ 12 = 0.0833（HALF_UP，4 位）
        assertThat(vo.getRefundRate()).isEqualByComparingTo("0.0833");
        assertThat(vo.getRefundAmount()).isEqualByComparingTo("12900");
        assertThat(vo.getRefundCount()).isEqualTo(1L);
        assertThat(vo.getGoodsTotal()).isEqualTo(215L);
        assertThat(vo.getUserTotal()).isEqualTo(48L);
        assertThat(vo.getLogErrorCount()).isEqualTo(2L);
        assertThat(vo.getUnavailable()).isEmpty();
    }

    @Test
    @DisplayName("客单价向下取整（不四舍五入）：999÷2=499.5 ⇒ 499 分")
    void avgOrderValueFloorsDown() {
        assertThat(DashboardService.avgOrderValue(new BigDecimal("999"), 2L)).isEqualByComparingTo("499");
        assertThat(DashboardService.avgOrderValue(new BigDecimal("1000"), 3L)).isEqualByComparingTo("333");
    }

    @Test
    @DisplayName("orderCount=0：客单价与退款率返回 null（前端显示 —，不得 NaN/0，§2.1.2/§8 step 7）")
    void zeroDenominatorYieldsNull() {
        when(orderClient.adminOrderStats(anyString())).thenReturn(
                stats(BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO));

        DashboardStatsVO vo = service.stats("today");

        assertThat(vo.getAvgOrderValue()).isNull();
        assertThat(vo.getRefundRate()).isNull();
        // 金额与计数仍为 0（空集显示 0，BR-RPT-08）
        assertThat(vo.getGmv()).isEqualByComparingTo("0");
        assertThat(vo.getOrderCount()).isZero();
        assertThat(vo.getRefundAmount()).isEqualByComparingTo("0");
        // 单日窗口的 trend 长度必须是 1
        assertThat(vo.getTrend()).hasSize(1);
    }

    @Test
    @DisplayName("订单域整体失败：六项进 unavailable、派生值为 null，接口仍返回对象不抛异常")
    void orderDomainFailureDegrades() {
        when(orderClient.adminOrderStats(anyString())).thenThrow(new IllegalStateException("order 超时"));
        when(goodsClient.adminCountGoods()).thenReturn(215L);
        when(userClient.adminCountUsers()).thenReturn(48L);
        when(logErrorCounter.todayCount()).thenReturn(null);

        DashboardStatsVO vo = service.stats("30d");

        assertThat(vo.getUnavailable()).containsExactlyInAnyOrder(
                "gmv", "refundAmount", "waitShipCount", "pendingAftersaleCount", "top5", "trend");
        assertThat(vo.getGmv()).isNull();
        assertThat(vo.getAvgOrderValue()).isNull();
        assertThat(vo.getRefundRate()).isNull();
        // 其它域不受牵连
        assertThat(vo.getGoodsTotal()).isEqualTo(215L);
        assertThat(vo.getUserTotal()).isEqualTo(48L);
        assertThat(vo.getLogErrorCount()).isNull();
        // 失败也要给前端合法的空数组，避免 undefined
        assertThat(vo.getTop5()).isEmpty();
        assertThat(vo.getTrend()).hasSize(30);
    }

    @Test
    @DisplayName("商品/用户总数单域失败：只该字段 null，不影响报表主体")
    void goodsOrUserFailureIsolated() {
        when(orderClient.adminOrderStats(anyString())).thenReturn(
                stats(new BigDecimal("9900"), 1L, 0L, BigDecimal.ZERO));
        when(goodsClient.adminCountGoods()).thenThrow(new IllegalStateException("goods 超时"));
        when(userClient.adminCountUsers()).thenReturn(48L);
        when(logErrorCounter.todayCount()).thenReturn(0L);

        DashboardStatsVO vo = service.stats("7d");

        assertThat(vo.getGoodsTotal()).isNull();
        assertThat(vo.getUserTotal()).isEqualTo(48L);
        assertThat(vo.getGmv()).isEqualByComparingTo("9900");
        assertThat(vo.getUnavailable()).isEmpty();
    }

    @Test
    @DisplayName("趋势补零：订单域只回 2 天，输出仍是完整 7 天且缺失日为 0")
    void trendZeroFilled() {
        OrderAdminStatsDTO dto = stats(new BigDecimal("9900"), 1L, 0L, BigDecimal.ZERO);
        String inWindow = LocalDate.now(StatsPeriod.ZONE).minusDays(3).toString();
        dto.setTrend(List.of(trend(inWindow, 1L, new BigDecimal("9900"))));
        when(orderClient.adminOrderStats("7d")).thenReturn(dto);
        when(goodsClient.adminCountGoods()).thenReturn(1L);
        when(userClient.adminCountUsers()).thenReturn(1L);
        when(logErrorCounter.todayCount()).thenReturn(0L);

        DashboardStatsVO vo = service.stats("7d");

        assertThat(vo.getTrend()).hasSize(7);
        assertThat(vo.getTrend()).allSatisfy(point -> {
            assertThat(point.getDate()).isNotBlank();
            assertThat(point.getOrderCount()).isNotNull();
            assertThat(point.getGmv()).isNotNull();
        });
        long nonZero = vo.getTrend().stream()
                .filter(point -> point.getOrderCount() > 0).count();
        assertThat(nonZero).isEqualTo(1L);
    }

    @Test
    @DisplayName("period 非枚举值静默按 30d 转发（不报错，§2.1.1）")
    void invalidPeriodFallsBackTo30d() {
        when(orderClient.adminOrderStats("30d")).thenReturn(
                stats(BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO));
        when(goodsClient.adminCountGoods()).thenReturn(1L);
        when(userClient.adminCountUsers()).thenReturn(1L);
        when(logErrorCounter.todayCount()).thenReturn(0L);

        DashboardStatsVO vo = service.stats("90d");

        assertThat(vo.getPeriod()).isEqualTo("30d");
        verify(orderClient).adminOrderStats("30d");
    }

    private OrderAdminStatsDTO stats(BigDecimal gmv, Long orderCount, Long refundCount,
                                     BigDecimal refundAmount) {
        OrderAdminStatsDTO dto = new OrderAdminStatsDTO();
        dto.setGmv(gmv);
        dto.setOrderCount(orderCount);
        dto.setRefundCount(refundCount);
        dto.setRefundAmount(refundAmount);
        dto.setWaitShipCount(2L);
        dto.setUndelivered(2L);
        dto.setPendingAftersaleCount(1L);
        dto.setTodayOrders(3L);
        dto.setTop5(List.of());
        dto.setTrend(List.of());
        return dto;
    }

    private OrderAdminStatsDTO.Trend trend(String date, Long orderCount, BigDecimal gmv) {
        OrderAdminStatsDTO.Trend point = new OrderAdminStatsDTO.Trend();
        point.setDate(date);
        point.setOrderCount(orderCount);
        point.setGmv(gmv);
        return point;
    }
}
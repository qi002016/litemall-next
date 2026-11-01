package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.client.OrderClient;
import com.litemall.client.UserClient;
import com.litemall.domain.DTO.OrderCloseExpiredReqDTO;
import com.litemall.domain.DTO.OrderCloseReqDTO;
import com.litemall.domain.DTO.OrderShipReqDTO;
import com.litemall.domain.DTO.OrderUpdateShipReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.DashboardStatsVO;
import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminDetailDTO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.domain.dto.OrderCloseDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.service.DashboardService;
import com.litemall.support.LogErrorCounter;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R1（R1-05）：管理端订单控制器经 Feign 转发后的行为单测。
 * <p>
 * 覆盖点：
 * </p>
 * <ul>
 *   <li>CI-26 列表：入参归一（pageNum/pageSize 缺省与上限、排序白名单、时间格式）后
 *       调用完整签名 {@code adminOrderList}，<b>不再调用旧 3 参</b>；</li>
 *   <li>CI-26 列表：{@code status}/{@code aftersaleStatus} 标识白名单，非法标识 400
 *       （修复「按已关闭筛选却返回全部订单」的静默失败）；</li>
 *   <li>CN-33 详情、CI-27 发货、CN-10 关单（透传 adminId）、CN-11 超时关单、CN-12 改物流；</li>
 *   <li>dashboard 统计分别来自三个域的 inner 接口。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderFeignTest {

    @Mock
    private OrderClient orderClient;

    private OrderController orderController;

    @BeforeEach
    void setUp() {
        orderController = new OrderController(orderClient);
        UserContext.removeUser();
    }

    @Test
    @DisplayName("R1：/admin/order/list 归一入参后调用完整筛选签名（pageNo/pageSize/sortBy/isAsc）")
    void listDelegatesToFullSignature() {
        OrderAdminDTO row = new OrderAdminDTO();
        row.setId(7L);
        row.setOrderSn("SN7");
        row.setUserId(1L);
        row.setOrderAmount(1990);
        row.setStatus("undelivered");
        row.setAddTime(LocalDateTime.of(2026, 9, 27, 10, 0));
        when(orderClient.adminOrderList(1, 20, "undelivered", null, null, null, null, null, null, "addTime", false))
                .thenReturn(new PageVO<>(1L, 1L, List.of(row)));

        Result<PageVO<OrderAdminDTO>> result =
                orderController.list(null, null, "undelivered", null, null, null, null, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getList().get(0).getStatus()).isEqualTo("undelivered");
        verify(orderClient).adminOrderList(1, 20, "undelivered", null, null, null, null, null, null, "addTime", false);
        verify(orderClient, never()).adminListOrders(any(), any(), any());
    }

    /**
     * D3 / P-11：订单列表默认 {@code addTime desc}（新单在前），显式 {@code isAsc=true} 时才升序。
     * <p>
     * 断言的是<b>行为</b>而非参数字符串：stub 模拟 order 侧 {@code ORDER BY add_time ASC|DESC}
     * （由 admin 转发过去的 isAsc 决定），再用两条 addTime 不同的订单验证返回顺序——
     * 若默认值退回 {@code true}，第一条断言会拿到「最旧在前」而失败。
     * </p>
     */
    @Test
    @DisplayName("D3/P-11：订单列表不传 isAsc → 新单在前（addTime desc）；显式 isAsc=true → 最旧在前")
    void listDefaultsToAddTimeDescButHonoursExplicitAsc() {
        OrderAdminDTO older = orderRow(11L, "SN11", LocalDateTime.of(2026, 9, 1, 9, 0));
        OrderAdminDTO newer = orderRow(22L, "SN22", LocalDateTime.of(2026, 11, 1, 18, 0));

        when(orderClient.adminOrderList(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // 模拟 order 侧：isAsc=true → addTime 升序；false → addTime 降序
                    boolean asc = invocation.getArgument(10, Boolean.class);
                    List<OrderAdminDTO> rows = new ArrayList<>(List.of(older, newer));
                    rows.sort(Comparator.comparing(OrderAdminDTO::getAddTime));
                    if (!asc) {
                        Collections.reverse(rows);
                    }
                    return new PageVO<>(2L, 1L, rows);
                });

        List<OrderAdminDTO> defaultRows = orderController
                .list(null, null, null, null, null, null, null, null, null, null, null)
                .getData().getList();
        assertThat(defaultRows).extracting(OrderAdminDTO::getId).containsExactly(22L, 11L);

        List<OrderAdminDTO> ascRows = orderController
                .list(null, null, null, null, null, null, null, null, null, null, true)
                .getData().getList();
        assertThat(ascRows).extracting(OrderAdminDTO::getId).containsExactly(11L, 22L);
    }

    @Test
    @DisplayName("R1：/admin/order/list 的 pageSize 超上限按 100、排序非法落默认、售后单 l 归一为双 l")
    void listNormalizesPageSizeSortAndTime() {
        when(orderClient.adminOrderList(2, 100, null, "waitReceive,cancelled", "SN1", "张三", 3L,
                "2026-01-01 00:00:00", "2026-01-31 23:59:59", "addTime", false))
                .thenReturn(new PageVO<>(0L, 0L, List.of()));

        orderController.list(2, 500, null, "waitReceive,canceled", "SN1", "张三", 3L,
                "2026-01-01 00:00:00", "2026-01-31 23:59:59", "bogus", false);

        verify(orderClient).adminOrderList(2, 100, null, "waitReceive,cancelled", "SN1", "张三", 3L,
                "2026-01-01 00:00:00", "2026-01-31 23:59:59", "addTime", false);
    }

    @Test
    @DisplayName("R1：/admin/order/list 的 status 非法标识 → 400 PARAM_INVALID（不再静默返回全部订单）")
    void listRejectsUnknownOrderStatus() {
        assertThatThrownBy(() -> orderController.list(1, 10, "closed", null, null, null, null, null, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("status 取值非法");

        verify(orderClient, never()).adminOrderList(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    @DisplayName("R1：/admin/order/list 的时间格式非法 → 400 PARAM_INVALID")
    void listRejectsBadTimeFormat() {
        assertThatThrownBy(() -> orderController.list(1, 10, null, null, null, null, null, "2026/01/01", null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("yyyy-MM-dd HH:mm:ss");
    }

    @Test
    @DisplayName("R1：/admin/order/{id} 直接透传 order 详情（CN-33）")
    void detailDelegatesToOrderClient() {
        OrderAdminDetailDTO detail = new OrderAdminDetailDTO();
        detail.setId(11L);
        detail.setOrderSn("SN11");
        when(orderClient.adminOrderDetail(11L)).thenReturn(detail);

        Result<OrderAdminDetailDTO> result = orderController.detail(11L);

        assertThat(result.getData().getOrderSn()).isEqualTo("SN11");
        verify(orderClient).adminOrderDetail(11L);
    }

    @Test
    @DisplayName("R1：/admin/order/ship 转发为 OrderShipDTO（含快递信息）")
    void shipDelegatesToOrderClient() {
        OrderShipReqDTO body = new OrderShipReqDTO();
        body.setOrderId(12L);
        body.setShipChannel("SF");
        body.setShipSn("SF999");

        orderController.ship(body);

        ArgumentCaptor<OrderShipDTO> captor = ArgumentCaptor.forClass(OrderShipDTO.class);
        verify(orderClient).adminShip(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(12L);
        assertThat(captor.getValue().getShipChannel()).isEqualTo("SF");
        assertThat(captor.getValue().getShipSn()).isEqualTo("SF999");
    }

    @Test
    @DisplayName("R1：/admin/order/close 转发关单并显式透传当前 adminId（P-19，不伪造 0）")
    void closePassesAdminId() {
        UserContext.setUser(88L);
        try {
            OrderCloseReqDTO body = new OrderCloseReqDTO();
            body.setOrderId(13L);
            body.setReason("用户电话协商关闭");

            orderController.close(body);

            ArgumentCaptor<OrderCloseDTO> captor = ArgumentCaptor.forClass(OrderCloseDTO.class);
            verify(orderClient).adminClose(captor.capture());
            assertThat(captor.getValue().getOrderId()).isEqualTo(13L);
            assertThat(captor.getValue().getReason()).isEqualTo("用户电话协商关闭");
            assertThat(captor.getValue().getAdminId()).isEqualTo(88L);
        } finally {
            UserContext.removeUser();
        }
    }

    @Test
    @DisplayName("R1：/admin/order/close-expired 缺省 limit=100/dryRun=false，显式传值原样转发")
    void closeExpiredDefaultsAndForwards() {
        CloseExpiredResultDTO result = new CloseExpiredResultDTO();
        when(orderClient.adminCloseExpired(100, false)).thenReturn(result);
        when(orderClient.adminCloseExpired(200, true)).thenReturn(result);

        orderController.closeExpired(null);

        OrderCloseExpiredReqDTO body = new OrderCloseExpiredReqDTO();
        body.setLimit(200);
        body.setDryRun(true);
        orderController.closeExpired(body);

        verify(orderClient).adminCloseExpired(100, false);
        verify(orderClient).adminCloseExpired(200, true);
    }

    @Test
    @DisplayName("R1：/admin/order/update-ship 转发新快递信息与原因（CN-12）")
    void updateShipDelegates() {
        OrderUpdateShipReqDTO body = new OrderUpdateShipReqDTO();
        body.setOrderId(14L);
        body.setShipChannel("YTO");
        body.setShipSn("YTO123");
        body.setReason("单号录错");

        orderController.updateShip(body);

        ArgumentCaptor<OrderShipDTO> captor = ArgumentCaptor.forClass(OrderShipDTO.class);
        verify(orderClient).adminUpdateShip(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(14L);
        assertThat(captor.getValue().getShipChannel()).isEqualTo("YTO");
        assertThat(captor.getValue().getShipSn()).isEqualTo("YTO123");
        assertThat(captor.getValue().getReason()).isEqualTo("单号录错");
    }

    @Test
    @DisplayName("R2（CI-08）：/admin/dashboard/stats 聚合 order/goods/user 三域，缺省 period=30d")
    void dashboardAggregatesThreeDomains() {
        GoodsClient goodsClient = mock(GoodsClient.class);
        UserClient userClient = mock(UserClient.class);
        LogErrorCounter logErrorCounter = mock(LogErrorCounter.class);
        when(orderClient.adminOrderStats("30d")).thenReturn(stats(5L, 3L));
        when(goodsClient.adminCountGoods()).thenReturn(20L);
        when(userClient.adminCountUsers()).thenReturn(30L);
        when(logErrorCounter.todayCount()).thenReturn(0L);

        DashboardController dashboard = new DashboardController(
                new DashboardService(goodsClient, orderClient, userClient, logErrorCounter));
        Result<DashboardStatsVO> result = dashboard.stats(null);

        assertThat(result.getData().getPeriod()).isEqualTo("30d");
        assertThat(result.getData().getGoodsTotal()).isEqualTo(20L);
        assertThat(result.getData().getUserTotal()).isEqualTo(30L);
        assertThat(result.getData().getTodayOrders()).isEqualTo(5L);
        assertThat(result.getData().getUndelivered()).isEqualTo(3L);
        assertThat(result.getData().getUnavailable()).isEmpty();
        // trend 必须补满 30 个自然日（§1.6）
        assertThat(result.getData().getTrend()).hasSize(30);
    }

    private OrderAdminDTO orderRow(Long id, String orderSn, LocalDateTime addTime) {
        OrderAdminDTO row = new OrderAdminDTO();
        row.setId(id);
        row.setOrderSn(orderSn);
        row.setAddTime(addTime);
        return row;
    }

    private OrderAdminStatsDTO stats(Long todayOrders, Long undelivered) {
        OrderAdminStatsDTO dto = new OrderAdminStatsDTO();
        dto.setPeriod("30d");
        dto.setTodayOrders(todayOrders);
        dto.setUndelivered(undelivered);
        dto.setWaitShipCount(undelivered);
        dto.setPendingAftersaleCount(1L);
        dto.setGmv(new BigDecimal("128800"));
        dto.setOrderCount(12L);
        dto.setRefundCount(1L);
        dto.setRefundAmount(new BigDecimal("12900"));
        dto.setTop5(Collections.emptyList());
        dto.setTrend(Collections.emptyList());
        return dto;
    }
}
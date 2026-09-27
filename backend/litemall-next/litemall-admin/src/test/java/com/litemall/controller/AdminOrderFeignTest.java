package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.client.OrderClient;
import com.litemall.client.UserClient;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.domain.dto.OrderRefundDTO;
import com.litemall.domain.dto.OrderShipDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H5：管理端订单控制器改为 Feign 调用后的行为单测。
 * <p>
 * 改造前 admin 直接操作 {@code AdminOrderMapper}（写 litemall_order）；现在必须
 * 原样转发到 order 服务的 {@code /inner/order/admin/**}，因此这里断言：
 * <ul>
 *   <li>列表直接透传 order 服务返回的分页数据（前端字段契约不变）；</li>
 *   <li>发货/退款把请求体（含 orderId/快递信息/退款金额）完整转成 DTO 交给 Feign 客户端；</li>
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
    }

    @Test
    @DisplayName("H5：/admin/order/list 直接透传 order 服务分页结果")
    void listDelegatesToOrderClient() {
        OrderAdminDTO row = new OrderAdminDTO();
        row.setId(7);
        row.setOrderSn("SN7");
        row.setUserId(1);
        row.setOrderAmount(new BigDecimal("19.90"));
        row.setStatus("undelivered");
        row.setAddTime(LocalDateTime.of(2026, 9, 27, 10, 0));
        when(orderClient.adminListOrders(1, 10, "undelivered"))
                .thenReturn(new PageVO<>(1L, 1L, List.of(row)));

        Result<PageVO<OrderAdminDTO>> result = orderController.list(1, 10, "undelivered");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getList().get(0).getStatus()).isEqualTo("undelivered");
        verify(orderClient).adminListOrders(1, 10, "undelivered");
    }

    @Test
    @DisplayName("H5：/admin/order/ship 转发为 OrderShipDTO（含快递信息）")
    void shipDelegatesToOrderClient() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", 12);
        body.put("shipChannel", "SF");
        body.put("shipSn", "SF999");

        orderController.ship(body);

        ArgumentCaptor<OrderShipDTO> captor = ArgumentCaptor.forClass(OrderShipDTO.class);
        verify(orderClient).adminShip(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(12L);
        assertThat(captor.getValue().getShipChannel()).isEqualTo("SF");
        assertThat(captor.getValue().getShipSn()).isEqualTo("SF999");
    }

    @Test
    @DisplayName("H5：/admin/order/refund 转发为 OrderRefundDTO（含退款金额与备注）")
    void refundDelegatesToOrderClient() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", 13);
        body.put("refundAmount", "66.60");
        body.put("refundContent", "协商退款");

        orderController.refund(body);

        ArgumentCaptor<OrderRefundDTO> captor = ArgumentCaptor.forClass(OrderRefundDTO.class);
        verify(orderClient).adminRefund(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(13L);
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("66.60");
        assertThat(captor.getValue().getRefundContent()).isEqualTo("协商退款");
    }

    @Test
    @DisplayName("H5：/admin/order/refund 仅传 orderId 时金额留空，由 order 服务兜底")
    void refundWithoutAmount_keepsAmountNull() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", 14);

        orderController.refund(body);

        ArgumentCaptor<OrderRefundDTO> captor = ArgumentCaptor.forClass(OrderRefundDTO.class);
        verify(orderClient).adminRefund(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(14L);
        assertThat(captor.getValue().getRefundAmount()).isNull();
    }

    @Test
    @DisplayName("H5：/admin/dashboard/stats 分别取 order/goods/user 三个域的 inner 统计")
    void dashboardAggregatesThreeDomains() {
        GoodsClient goodsClient = mock(GoodsClient.class);
        UserClient userClient = mock(UserClient.class);
        when(orderClient.adminOrderStats()).thenReturn(stats(5L, 3L));
        when(goodsClient.adminCountGoods()).thenReturn(20L);
        when(userClient.adminCountUsers()).thenReturn(30L);

        DashboardController dashboard = new DashboardController(goodsClient, orderClient, userClient);
        Result<Map<String, Object>> result = dashboard.stats();

        assertThat(result.getData())
                .containsEntry("goodsTotal", 20L)
                .containsEntry("userTotal", 30L)
                .containsEntry("todayOrders", 5L)
                .containsEntry("undelivered", 3L);
    }

    private OrderAdminStatsDTO stats(Long todayOrders, Long undelivered) {
        OrderAdminStatsDTO dto = new OrderAdminStatsDTO();
        dto.setTodayOrders(todayOrders);
        dto.setUndelivered(undelivered);
        return dto;
    }
}
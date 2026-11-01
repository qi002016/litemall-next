package com.litemall.controller;

import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.domain.dto.OrderCloseDTO;
import com.litemall.domain.dto.OrderGoodsStatusDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.service.IOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H5：管理端订单内部接口的接线级测试。
 * <p>
 * 验证 {@code /inner/order/**} 端点真实存在、受 H3 内部密钥拦截器保护，
 * 且订单服务的业务拒绝以 400 + 原始原因返回给 admin（Feign 需要非 200 才能感知失败），
 * 新增端点（CN-10 关单 / CN-11 超时关单 / CN-IN-01 行状态 / CN-IN-04 锁量）请求映射正确。
 * 业务规则本身见 {@code OrderAdminStateMachineTest} / {@code OrderLockedStockTest}。
 * </p>
 * <p><b>CX-03</b>：{@code /inner/order/admin/refund} 已下线，不再有对应接线测试。</p>
 */
@SpringBootTest(properties = "hm.internal.token=test-inner-secret")
class OrderAdminInnerApiTest {

    private static final String TOKEN = "test-inner-secret";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private IOrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("H5：非法状态流转（未付款发货）经内部接口返回 400 与原始业务原因")
    void adminShipIllegalTransition_returns400WithReason() throws Exception {
        doThrow(new BadRequestException("仅待发货订单可发货，当前状态：unpaid"))
                .when(orderService).adminShip(any());

        mockMvc.perform(post("/inner/order/admin/ship")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("仅待发货订单可发货，当前状态：unpaid"));
    }

    @Test
    @DisplayName("H5：合法发货流转经内部接口返回 200，并把请求体映射为 OrderShipDTO")
    void adminShipLegalTransition_returns200() throws Exception {
        mockMvc.perform(post("/inner/order/admin/ship")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":3,\"shipChannel\":\"SF\",\"shipSn\":\"SF001\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderShipDTO> captor = ArgumentCaptor.forClass(OrderShipDTO.class);
        verify(orderService).adminShip(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(3L);
        assertThat(captor.getValue().getShipChannel()).isEqualTo("SF");
        assertThat(captor.getValue().getShipSn()).isEqualTo("SF001");
    }

    @Test
    @DisplayName("H5：CN-10 关单请求体映射为 OrderCloseDTO（含 reason / adminId）")
    void adminCloseMapsBody() throws Exception {
        mockMvc.perform(post("/inner/order/admin/close")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":5,\"reason\":\"运营核实恶意占单\",\"adminId\":9001}"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderCloseDTO> captor = ArgumentCaptor.forClass(OrderCloseDTO.class);
        verify(orderService).adminClose(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(5L);
        assertThat(captor.getValue().getReason()).isEqualTo("运营核实恶意占单");
        assertThat(captor.getValue().getAdminId()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("H5：CN-11 超时关单透传 limit / dryRun，返回统计结构")
    void adminCloseExpiredPassesParams() throws Exception {
        CloseExpiredResultDTO result = new CloseExpiredResultDTO();
        result.setTotal(2);
        result.setSucceeded(2);
        when(orderService.adminCloseExpired(eq(5), eq(true))).thenReturn(result);

        mockMvc.perform(post("/inner/order/admin/close-expired")
                        .header("X-Internal-Token", TOKEN)
                        .param("limit", "5")
                        .param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.succeeded").value(2));

        verify(orderService).adminCloseExpired(5, true);
    }

    @Test
    @DisplayName("H5：CN-IN-01 行状态路径参数与 userId 查询参数映射正确")
    void orderGoodsStatusMapsPath() throws Exception {
        when(orderService.orderGoodsStatus(6L, 7L)).thenReturn(new OrderGoodsStatusDTO());

        mockMvc.perform(get("/inner/order/admin/order-goods/6")
                        .header("X-Internal-Token", TOKEN)
                        .param("userId", "7"))
                .andExpect(status().isOk());

        verify(orderService).orderGoodsStatus(6L, 7L);
    }

    @Test
    @DisplayName("H5：CN-IN-04 锁量查询透传 productIds 原始串")
    void lockedStockMapsQuery() throws Exception {
        mockMvc.perform(get("/inner/order/locked-stock")
                        .header("X-Internal-Token", TOKEN)
                        .param("productIds", "1001,1002"))
                .andExpect(status().isOk());

        verify(orderService).lockedStock("1001,1002");
    }

    @Test
    @DisplayName("H5：新增的管理端内部接口同样受 X-Internal-Token 保护（无密钥 401）")
    void adminInnerApiWithoutToken_unauthorized() throws Exception {
        mockMvc.perform(get("/inner/order/admin/list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
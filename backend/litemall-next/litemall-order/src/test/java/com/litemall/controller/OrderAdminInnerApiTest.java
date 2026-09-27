package com.litemall.controller;

import com.litemall.domain.dto.OrderRefundDTO;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H5：管理端订单内部接口的接线级测试。
 * <p>
 * 验证新增的 {@code /inner/order/admin/**} 端点真实存在、受 H3 内部密钥拦截器保护，
 * 且订单服务的业务拒绝（{@code BadRequestException}）以 400 + 原始原因返回给 admin，
 * 管理端因此能拿到"仅待发货订单可发货"这类可读提示，而不是 500。
 * 业务规则本身见 {@code OrderAdminStateMachineTest}。这里用 mock 替换 {@link IOrderService}
 * 以避免触碰数据库。
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
    @DisplayName("H5：已取消订单退款经内部接口返回 400")
    void adminRefundCanceledOrder_returns400() throws Exception {
        doThrow(new BadRequestException("订单已取消，不可退款，当前状态：canceled"))
                .when(orderService).adminRefund(any());

        mockMvc.perform(post("/inner/order/admin/refund")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("订单已取消，不可退款，当前状态：canceled"));
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
    @DisplayName("H5：退款请求体映射为 OrderRefundDTO（金额文本 → BigDecimal）")
    void adminRefundMapsBody() throws Exception {
        mockMvc.perform(post("/inner/order/admin/refund")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":4,\"refundAmount\":\"12.30\",\"refundContent\":\"协商\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderRefundDTO> captor = ArgumentCaptor.forClass(OrderRefundDTO.class);
        verify(orderService).adminRefund(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(4L);
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("12.30");
        assertThat(captor.getValue().getRefundContent()).isEqualTo("协商");
    }

    @Test
    @DisplayName("H5：新增的管理端内部接口同样受 X-Internal-Token 保护（无密钥 401）")
    void adminInnerApiWithoutToken_unauthorized() throws Exception {
        mockMvc.perform(get("/inner/order/admin/list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
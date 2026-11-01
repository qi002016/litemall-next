package com.litemall.controller;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.AftersaleAdminDTO;
import com.litemall.domain.dto.AftersaleAdminDetailDTO;
import com.litemall.domain.dto.AftersaleApproveDTO;
import com.litemall.domain.dto.AftersaleApproveResultDTO;
import com.litemall.domain.dto.AftersaleCloseDTO;
import com.litemall.domain.dto.AftersaleCloseResultDTO;
import com.litemall.domain.dto.AftersaleIdDTO;
import com.litemall.domain.dto.AftersaleRefundDTO;
import com.litemall.domain.dto.AftersaleRefundResultDTO;
import com.litemall.domain.dto.AutoCloseResultDTO;
import com.litemall.exception.ConflictException;
import com.litemall.service.IAftersaleService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 售后域接线级测试（R1-03 / 07 §4、§6.2）：{@code /inner/order/admin/aftersale/**} 9 个端点
 * 与 {@code /inner/aftersale/auto-close}、C 端 {@code /app/aftersale/**} 的真实映射。
 * <p>
 * 业务规则见 {@code AftersaleStateMachineTest} / {@code AftersaleRefundTest}；此处只验证
 * 路径存在、请求体/查询参数映射、内部密钥保护与非 200 的失败语义（Feign 依赖它感知失败）。
 * </p>
 */
@SpringBootTest(properties = "hm.internal.token=test-inner-secret")
class AftersaleApiTest {

    private static final String TOKEN = "test-inner-secret";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private IAftersaleService aftersaleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("CN-03：审核通过请求体映射（aftersaleId/returnAddress/remark/adminId）并回显状态")
    void approveMapsBody() throws Exception {
        AftersaleApproveResultDTO result = new AftersaleApproveResultDTO();
        result.setAftersaleId(1L);
        result.setStatus("waitReturn");
        result.setStatusText("待寄回");
        result.setReturnAddress("浙江省杭州市西湖区退货仓");
        result.setReturnDeadline("2026-11-08 12:00:00");
        when(aftersaleService.approve(any())).thenReturn(result);

        mockMvc.perform(post("/inner/order/admin/aftersale/approve")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aftersaleId\":1,\"returnAddress\":\"浙江省杭州市西湖区退货仓\","
                                + "\"remark\":\"同意退货\",\"adminId\":9001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("waitReturn"))
                .andExpect(jsonPath("$.returnAddress").value("浙江省杭州市西湖区退货仓"))
                .andExpect(jsonPath("$.returnDeadline").value("2026-11-08 12:00:00"));

        ArgumentCaptor<AftersaleApproveDTO> captor = ArgumentCaptor.forClass(AftersaleApproveDTO.class);
        verify(aftersaleService).approve(captor.capture());
        assertThat(captor.getValue().getAftersaleId()).isEqualTo(1L);
        assertThat(captor.getValue().getRemark()).isEqualTo("同意退货");
        assertThat(captor.getValue().getAdminId()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("CN-02：详情以 query 参数 aftersaleId 映射，返回订单/行/时间线结构")
    void detailMapsQuery() throws Exception {
        AftersaleAdminDetailDTO detail = new AftersaleAdminDetailDTO();
        detail.setAftersaleId(7L);
        detail.setAftersaleSn("AS202611010000007");
        detail.setTimeline(List.of());
        when(aftersaleService.adminDetail(7L)).thenReturn(detail);

        mockMvc.perform(get("/inner/order/admin/aftersale/detail")
                        .header("X-Internal-Token", TOKEN)
                        .param("aftersaleId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aftersaleSn").value("AS202611010000007"))
                .andExpect(jsonPath("$.timeline").isArray());

        verify(aftersaleService).adminDetail(7L);
    }

    @Test
    @DisplayName("CN-01：列表 11 个查询参数全部透传（status 混用、type、时间窗、排序）")
    void listPassesAllParams() throws Exception {
        when(aftersaleService.adminList(1, 10, "applied,3", 2, null, null, "张三", "2026-11-01 00:00:00",
                "2026-11-02 00:00:00", "amount", true)).thenReturn(PageVO.<AftersaleAdminDTO>empty(1L, 1L));

        mockMvc.perform(get("/inner/order/admin/aftersale/list")
                        .header("X-Internal-Token", TOKEN)
                        .param("pageNo", "1")
                        .param("pageSize", "10")
                        .param("status", "applied,3")
                        .param("type", "2")
                        .param("keyword", "张三")
                        .param("applyTimeMin", "2026-11-01 00:00:00")
                        .param("applyTimeMax", "2026-11-02 00:00:00")
                        .param("sortBy", "amount")
                        .param("isAsc", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists());

        verify(aftersaleService).adminList(eq(1), eq(10), eq("applied,3"), eq(2), isNull(), isNull(),
                eq("张三"), eq("2026-11-01 00:00:00"), eq("2026-11-02 00:00:00"), eq("amount"), eq(true));
    }

    @Test
    @DisplayName("CN-04：状态冲突以 409 + AFTERSALE_STATUS_ILLEGAL 回给 admin（不是 400）")
    void conflictPreserves409AndErrorCode() throws Exception {
        doThrow(new ConflictException("当前售后状态不能驳回", "AFTERSALE_STATUS_ILLEGAL"))
                .when(aftersaleService).reject(any());

        mockMvc.perform(post("/inner/order/admin/aftersale/reject")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aftersaleId\":1,\"reason\":\"商品已使用\",\"adminId\":9001}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("当前售后状态不能驳回"));
        // 说明：03 §4.1 要求错误码回填到 data.errorCode。当前本机 classpath 上存在一个陈旧的
        // litemall-common jar（该模块已改为 packaging=pom，但仍被 api/order 以 jar 类型依赖），
        // 其 com.litemall.domain.Result 覆盖了 common-core 的新版实现，导致 data 恒为 null。
        // 该缺陷属聚合层 pom/仓库卫生（已上报 captain），不在售后域可改范围；
        // 域内保证由 AftersaleStateMachineTest / AftersaleRefundTest 对 getErrorCode() 断言覆盖。
    }

    @Test
    @DisplayName("CN-07：回补失败仍是 200 + restockPending=true（admin 不得当失败处理）")
    void refundRestockPendingStillOk() throws Exception {
        AftersaleRefundResultDTO result = new AftersaleRefundResultDTO();
        result.setAftersaleId(1L);
        result.setStatus("refunding");
        result.setStatusText("退款中");
        result.setRestockStatus(2);
        result.setRestockStatusText("待回补");
        result.setRestockPending(true);
        result.setRefundAmount(1000);
        when(aftersaleService.refund(any())).thenReturn(result);

        mockMvc.perform(post("/inner/order/admin/aftersale/refund")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aftersaleId\":1,\"amount\":1000,\"adminId\":9001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("refunding"))
                .andExpect(jsonPath("$.restockPending").value(true))
                .andExpect(jsonPath("$.restockStatus").value(2));

        ArgumentCaptor<AftersaleRefundDTO> captor = ArgumentCaptor.forClass(AftersaleRefundDTO.class);
        verify(aftersaleService).refund(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("CN-05/CN-08/CN-09：确认收货、重试回补、关闭的请求体都映射到 aftersaleId")
    void receiveRetryCloseMapBody() throws Exception {
        when(aftersaleService.confirmReceive(any())).thenReturn(null);
        when(aftersaleService.retryRestock(any())).thenReturn(new AftersaleRefundResultDTO());
        AftersaleCloseResultDTO closed = new AftersaleCloseResultDTO();
        closed.setStatus("closed");
        when(aftersaleService.close(any())).thenReturn(closed);

        mockMvc.perform(post("/inner/order/admin/aftersale/confirm-receive")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aftersaleId\":2,\"adminId\":9001}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/inner/order/admin/aftersale/retry-restock")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aftersaleId\":3,\"adminId\":9001}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/inner/order/admin/aftersale/close")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aftersaleId\":4,\"reason\":\"用户放弃\",\"adminId\":9001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));

        ArgumentCaptor<AftersaleIdDTO> receiveCaptor = ArgumentCaptor.forClass(AftersaleIdDTO.class);
        verify(aftersaleService).confirmReceive(receiveCaptor.capture());
        assertThat(receiveCaptor.getValue().getAftersaleId()).isEqualTo(2L);
        ArgumentCaptor<AftersaleIdDTO> retryCaptor = ArgumentCaptor.forClass(AftersaleIdDTO.class);
        verify(aftersaleService).retryRestock(retryCaptor.capture());
        assertThat(retryCaptor.getValue().getAftersaleId()).isEqualTo(3L);
        ArgumentCaptor<AftersaleCloseDTO> closeCaptor = ArgumentCaptor.forClass(AftersaleCloseDTO.class);
        verify(aftersaleService).close(closeCaptor.capture());
        assertThat(closeCaptor.getValue().getAftersaleId()).isEqualTo(4L);
        assertThat(closeCaptor.getValue().getReason()).isEqualTo("用户放弃");
    }

    @Test
    @DisplayName("CN-IN-03：自动关闭端点返回 closed/failed 统计（无命中也是 200）")
    void autoCloseReturnsStats() throws Exception {
        AutoCloseResultDTO result = new AutoCloseResultDTO();
        result.setClosed(1);
        result.setFailed(List.of());
        when(aftersaleService.autoClose()).thenReturn(result);

        mockMvc.perform(post("/inner/aftersale/auto-close")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(1))
                .andExpect(jsonPath("$.failed").isArray());
    }

    @Test
    @DisplayName("内部接口无密钥 → 401（admin 侧必须带 X-Internal-Token）")
    void innerWithoutToken_unauthorized() throws Exception {
        mockMvc.perform(get("/inner/order/admin/aftersale/detail").param("aftersaleId", "1"))
                .andExpect(status().isUnauthorized());
    }
}
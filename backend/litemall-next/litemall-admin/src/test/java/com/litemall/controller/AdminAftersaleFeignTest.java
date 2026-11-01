package com.litemall.controller;

import com.litemall.client.OrderClient;
import com.litemall.domain.DTO.AftersaleApproveReqDTO;
import com.litemall.domain.DTO.AftersaleCloseReqDTO;
import com.litemall.domain.DTO.AftersaleIdReqDTO;
import com.litemall.domain.DTO.AftersaleRefundReqDTO;
import com.litemall.domain.DTO.AftersaleRejectReqDTO;
import com.litemall.domain.DTO.AftersaleSkipReceiveReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.AftersaleAdminDTO;
import com.litemall.domain.dto.AftersaleAdminDetailDTO;
import com.litemall.domain.dto.AftersaleApproveDTO;
import com.litemall.domain.dto.AftersaleApproveResultDTO;
import com.litemall.domain.dto.AftersaleCloseDTO;
import com.litemall.domain.dto.AftersaleCloseResultDTO;
import com.litemall.domain.dto.AftersaleIdDTO;
import com.litemall.domain.dto.AftersaleReceiveResultDTO;
import com.litemall.domain.dto.AftersaleRefundDTO;
import com.litemall.domain.dto.AftersaleRefundResultDTO;
import com.litemall.domain.dto.AftersaleRejectDTO;
import com.litemall.domain.dto.AftersaleRejectResultDTO;
import com.litemall.domain.dto.AftersaleSkipReceiveDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R1（R1-05）：管理端售后控制器（CN-01 ~ CN-09）转发行为单测。
 * <p>
 * 关注三件事：①入参归一（pageNum/pageSize、时间格式、状态白名单、单双 l 归一）；
 * ②P-19 adminId 显式透传；③admin 不改写下游结果（尤其 P-25 的
 * 200 + {@code restockPending=true} 必须原样返回，不能当失败）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AdminAftersaleFeignTest {

    @Mock
    private OrderClient orderClient;

    private AftersaleController controller;

    @BeforeEach
    void setUp() {
        controller = new AftersaleController(orderClient);
        UserContext.setUser(99L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("CN-01：列表入参归一后转发（pageNo/pageSize/状态归一/排序默认 applyTime）")
    void listNormalizesAndForwards() {
        when(orderClient.adminAftersaleList(1, 20, "applied,cancelled", 2, null, "SN1", "张三",
                null, null, "applyTime", true)).thenReturn(new PageVO<>(0L, 0L, List.of()));

        Result<PageVO<AftersaleAdminDTO>> result =
                controller.list(null, null, "applied,canceled", 2, null, "SN1", "张三", null, null, null, null);

        assertThat(result.getCode()).isEqualTo(200);
        verify(orderClient).adminAftersaleList(1, 20, "applied,cancelled", 2, null, "SN1", "张三",
                null, null, "applyTime", true);
    }

    @Test
    @DisplayName("CN-01：状态非法标识 → 400 PARAM_INVALID，不打下游")
    void listRejectsUnknownStatus() {
        assertThatThrownBy(() -> controller.list(1, 20, "done", null, null, null, null, null, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("status 取值非法：done");
        verify(orderClient, never()).adminAftersaleList(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("CN-01：type 越界 → 400 PARAM_INVALID")
    void listRejectsUnknownType() {
        assertThatThrownBy(() -> controller.list(1, 20, null, 5, null, null, null, null, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("type 取值非法：5");
    }

    @Test
    @DisplayName("CN-02：详情按 aftersaleId 转发")
    void detailForwards() {
        AftersaleAdminDetailDTO detail = new AftersaleAdminDetailDTO();
        when(orderClient.adminAftersaleDetail(5L)).thenReturn(detail);

        assertThat(controller.detail(5L).getData()).isSameAs(detail);
        verify(orderClient).adminAftersaleDetail(5L);
    }

    @Test
    @DisplayName("CN-03：审核通过透传 adminId + 退货地址（trim）")
    void approvePassesAdminIdAndAddress() {
        AftersaleApproveReqDTO body = new AftersaleApproveReqDTO();
        body.setAftersaleId(6L);
        body.setReturnAddress("  杭州市余杭区某路 1 号  ");
        body.setRemark("已核实");
        when(orderClient.adminAftersaleApprove(any())).thenReturn(new AftersaleApproveResultDTO());

        controller.approve(body);

        ArgumentCaptor<AftersaleApproveDTO> captor = ArgumentCaptor.forClass(AftersaleApproveDTO.class);
        verify(orderClient).adminAftersaleApprove(captor.capture());
        assertThat(captor.getValue().getAftersaleId()).isEqualTo(6L);
        assertThat(captor.getValue().getReturnAddress()).isEqualTo("杭州市余杭区某路 1 号");
        assertThat(captor.getValue().getRemark()).isEqualTo("已核实");
        assertThat(captor.getValue().getAdminId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("CN-03：退货地址留空则不传（由 order 侧读全局配置，admin 不读 common.yaml）")
    void approveKeepsAddressNullWhenBlank() {
        AftersaleApproveReqDTO body = new AftersaleApproveReqDTO();
        body.setAftersaleId(7L);
        body.setReturnAddress("   ");
        when(orderClient.adminAftersaleApprove(any())).thenReturn(new AftersaleApproveResultDTO());

        controller.approve(body);

        ArgumentCaptor<AftersaleApproveDTO> captor = ArgumentCaptor.forClass(AftersaleApproveDTO.class);
        verify(orderClient).adminAftersaleApprove(captor.capture());
        assertThat(captor.getValue().getReturnAddress()).isNull();
    }

    @Test
    @DisplayName("CN-04：驳回透传原因与 adminId")
    void rejectPassesReason() {
        AftersaleRejectReqDTO body = new AftersaleRejectReqDTO();
        body.setAftersaleId(8L);
        body.setReason("商品已使用");
        when(orderClient.adminAftersaleReject(any())).thenReturn(new AftersaleRejectResultDTO());

        controller.reject(body);

        ArgumentCaptor<AftersaleRejectDTO> captor = ArgumentCaptor.forClass(AftersaleRejectDTO.class);
        verify(orderClient).adminAftersaleReject(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("商品已使用");
        assertThat(captor.getValue().getAdminId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("CN-05：确认收货运 adminId")
    void confirmReceivePassesAdminId() {
        AftersaleIdReqDTO body = new AftersaleIdReqDTO();
        body.setAftersaleId(9L);
        when(orderClient.adminAftersaleConfirmReceive(any())).thenReturn(new AftersaleReceiveResultDTO());

        controller.confirmReceive(body);

        ArgumentCaptor<AftersaleIdDTO> captor = ArgumentCaptor.forClass(AftersaleIdDTO.class);
        verify(orderClient).adminAftersaleConfirmReceive(captor.capture());
        assertThat(captor.getValue().getAftersaleId()).isEqualTo(9L);
        assertThat(captor.getValue().getAdminId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("CN-06：跳过收货透传原因与 adminId")
    void skipReceivePassesReason() {
        AftersaleSkipReceiveReqDTO body = new AftersaleSkipReceiveReqDTO();
        body.setAftersaleId(10L);
        body.setReason("客户已自寄");
        when(orderClient.adminAftersaleSkipReceive(any())).thenReturn(new AftersaleReceiveResultDTO());

        controller.skipReceive(body);

        ArgumentCaptor<AftersaleSkipReceiveDTO> captor = ArgumentCaptor.forClass(AftersaleSkipReceiveDTO.class);
        verify(orderClient).adminAftersaleSkipReceive(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("客户已自寄");
        assertThat(captor.getValue().getAdminId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("CN-07：退款金额原样透传（一致性校验在下游），P-25 的 restockPending 原样返回")
    void refundPassesAmountAndKeepsRestockPending() {
        AftersaleRefundReqDTO body = new AftersaleRefundReqDTO();
        body.setAftersaleId(11L);
        body.setAmount(1990);
        AftersaleRefundResultDTO downstream = new AftersaleRefundResultDTO();
        downstream.setRestockPending(true);
        downstream.setStatus("refunding");
        when(orderClient.adminAftersaleRefund(any())).thenReturn(downstream);

        Result<AftersaleRefundResultDTO> result = controller.refund(body);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getRestockPending()).isTrue();
        assertThat(result.getData().getStatus()).isEqualTo("refunding");

        ArgumentCaptor<AftersaleRefundDTO> captor = ArgumentCaptor.forClass(AftersaleRefundDTO.class);
        verify(orderClient).adminAftersaleRefund(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(1990);
        assertThat(captor.getValue().getAdminId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("CN-08：重试回补透传 adminId")
    void retryRestockPassesAdminId() {
        AftersaleIdReqDTO body = new AftersaleIdReqDTO();
        body.setAftersaleId(12L);
        when(orderClient.adminAftersaleRetryRestock(any())).thenReturn(new AftersaleRefundResultDTO());

        controller.retryRestock(body);

        ArgumentCaptor<AftersaleIdDTO> captor = ArgumentCaptor.forClass(AftersaleIdDTO.class);
        verify(orderClient).adminAftersaleRetryRestock(captor.capture());
        assertThat(captor.getValue().getAdminId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("CN-09：关闭透传原因与 adminId")
    void closePassesReason() {
        AftersaleCloseReqDTO body = new AftersaleCloseReqDTO();
        body.setAftersaleId(13L);
        body.setReason("重复申请");
        when(orderClient.adminAftersaleClose(any())).thenReturn(new AftersaleCloseResultDTO());

        controller.close(body);

        ArgumentCaptor<AftersaleCloseDTO> captor = ArgumentCaptor.forClass(AftersaleCloseDTO.class);
        verify(orderClient).adminAftersaleClose(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("重复申请");
        assertThat(captor.getValue().getAdminId()).isEqualTo(99L);
    }
}
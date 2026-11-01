package com.litemall.service;

import com.litemall.client.OrderClient;
import com.litemall.domain.DTO.RefundDTO;
import com.litemall.domain.PO.Refund;
import com.litemall.domain.VO.PayParamsVO;
import com.litemall.domain.VO.PayResultVO;
import com.litemall.domain.VO.RefundVO;
import com.litemall.domain.dto.OrderPayInfoDTO;
import com.litemall.domain.dto.PayRefundDTO;
import com.litemall.domain.dto.PayRefundResultDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.CommonException;
import com.litemall.exception.ForbiddenException;
import com.litemall.mapper.RefundMapper;
import com.litemall.service.impl.PayServiceImpl;
import com.litemall.service.notify.NotifyResult;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>
 * 支付服务单元测试（纯 Mockito，不加载 Spring 上下文）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@ExtendWith(MockitoExtension.class)
class PayServiceImplTest {

    @Mock
    private OrderClient orderClient;

    @Mock
    private RefundMapper refundMapper;

    @Mock
    private com.litemall.service.notify.PayNotifyVerifier notifyVerifier;

    @Mock
    private com.litemall.service.notify.PayNotifyIdempotent notifyIdempotent;

    private PayServiceImpl payService;

    @BeforeEach
    void setUp() {
        UserContext.setUser(1L);
        // ServiceImpl 的 baseMapper 为父类 protected 字段，Mockito @InjectMocks 无法注入，显式设置
        payService = new PayServiceImpl(orderClient, notifyVerifier, notifyIdempotent);
        ReflectionTestUtils.setField(payService, "baseMapper", refundMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /** 探针契约：订单不存在时下单支付必须拒绝 */
    @Test
    @DisplayName("订单不存在时微信支付下单拒绝")
    void wechatPay_orderNotExist_rejects() {
        when(orderClient.getPayInfo(100L)).thenReturn(null);

        CommonException ex = assertThrows(CommonException.class, () -> payService.wechatPay(100L));
        assertEquals(404, ex.getCode());
        assertEquals("ORDER_NOT_FOUND", ex.getErrorCode());
        verify(orderClient).getPayInfo(100L);
    }

    /** 探针契约：订单属于他人时下单支付必须拒绝（权限边界） */
    @Test
    @DisplayName("订单属于他人时微信支付下单拒绝")
    void wechatPay_otherUserOrder_forbidden() {
        OrderPayInfoDTO info = payInfo(200L, 2L, 101, null, null);
        when(orderClient.getPayInfo(200L)).thenReturn(info);

        assertThrows(ForbiddenException.class, () -> payService.wechatPay(200L));
    }

    @Test
    @DisplayName("已支付订单不允许再次下单支付")
    void wechatPay_alreadyPaid_rejects() {
        OrderPayInfoDTO info = payInfo(300L, 1L, 102, LocalDateTime.now(), "wx123");
        when(orderClient.getPayInfo(300L)).thenReturn(info);

        // 03 卡 §4.4：CI-103 非 101 为 409 ORDER_ALREADY_PAID / ORDER_PAYABLE
        CommonException ex = assertThrows(CommonException.class, () -> payService.wechatPay(300L));
        assertEquals(409, ex.getCode());
    }

    @Test
    @DisplayName("未支付订单返回微信支付参数")
    void wechatPay_unpaid_returnsParams() {
        when(orderClient.getPayInfo(400L)).thenReturn(payInfo(400L, 1L, 101, null, null));

        PayParamsVO vo = payService.wechatPay(400L);

        assertNotNull(vo.getTimeStamp());
        assertNotNull(vo.getNonceStr());
        assertTrue(vo.getPackage_().startsWith("prepay_id=wx"));
        assertEquals("RSA", vo.getSignType());
        assertNotNull(vo.getPaySign());
    }

    @Test
    @DisplayName("回调缺少订单ID时拒绝")
    void notifyWechat_missingOrderId_rejects() {
        when(notifyVerifier.verify("s", "t", "n", "sig", "{}"))
                .thenReturn(new NotifyResult(null, "wx1", null));

        assertThrows(BadRequestException.class,
                () -> payService.notifyWechat("s", "t", "n", "sig", "{}"));
        verify(orderClient, never()).markPaid(any());
    }

    @Test
    @DisplayName("H2：验签失败时不得触碰订单状态")
    void notifyWechat_verifyFails_rejects() {
        when(notifyVerifier.verify(any(), any(), any(), any(), any()))
                .thenThrow(new BadRequestException("回调验签失败"));

        assertThrows(BadRequestException.class,
                () -> payService.notifyWechat("s", "t", "n", "bad", "{}"));
        verify(orderClient, never()).markPaid(any());
        verify(notifyIdempotent, never()).firstSeen(any());
    }

    @Test
    @DisplayName("回调通知订单服务标记支付成功")
    void notifyWechat_ok_markPaid() {
        when(notifyVerifier.verify("s", "t", "n", "sig", "{}"))
                .thenReturn(new NotifyResult(500L, "wxpay500", null));
        when(notifyIdempotent.firstSeen("wxpay500")).thenReturn(true);

        payService.notifyWechat("s", "t", "n", "sig", "{}");

        ArgumentCaptor<com.litemall.domain.dto.PaySuccessDTO> captor =
                ArgumentCaptor.forClass(com.litemall.domain.dto.PaySuccessDTO.class);
        verify(orderClient).markPaid(captor.capture());
        assertEquals(500L, captor.getValue().getOrderId());
        assertEquals("wxpay500", captor.getValue().getPayId());
    }

    @Test
    @DisplayName("H2：重复回调幂等——不再二次入账")
    void notifyWechat_duplicate_skips() {
        when(notifyVerifier.verify("s", "t", "n", "sig", "{}"))
                .thenReturn(new NotifyResult(501L, "wxpay501", null));
        when(notifyIdempotent.firstSeen("wxpay501")).thenReturn(false);

        payService.notifyWechat("s", "t", "n", "sig", "{}");

        verify(orderClient, never()).markPaid(any());
    }

    @Test
    @DisplayName("H2：回调金额与订单实付不一致时拒绝，且不释放幂等占位（防篡改重放）")
    void notifyWechat_amountMismatch_rejects() {
        when(notifyVerifier.verify("s", "t", "n", "sig", "{}"))
                .thenReturn(new NotifyResult(502L, "wxpay502", 1));
        when(notifyIdempotent.firstSeen("wxpay502")).thenReturn(true);
        when(orderClient.getPayInfo(502L)).thenReturn(payInfo(502L, 1L, 101, null, null));

        assertThrows(BadRequestException.class,
                () -> payService.notifyWechat("s", "t", "n", "sig", "{}"));
        verify(orderClient, never()).markPaid(any());
        // 金额不符属于永久性校验失败：保留幂等键，使后续同支付单号的重放被直接忽略
        verify(notifyIdempotent, never()).release(any());
    }

    @Test
    @DisplayName("H2：入账失败时释放幂等占位，允许微信重试")
    void notifyWechat_markPaidFails_releasesIdempotent() {
        when(notifyVerifier.verify("s", "t", "n", "sig", "{}"))
                .thenReturn(new NotifyResult(503L, "wxpay503", null));
        when(notifyIdempotent.firstSeen("wxpay503")).thenReturn(true);
        doThrow(new RuntimeException("rpc down")).when(orderClient).markPaid(any());

        assertThrows(RuntimeException.class,
                () -> payService.notifyWechat("s", "t", "n", "sig", "{}"));
        verify(notifyIdempotent).release("wxpay503");
    }

    @Test
    @DisplayName("未付款订单不允许申请退款")
    void refund_unpaidOrder_rejects() {
        when(orderClient.getPayInfo(600L)).thenReturn(payInfo(600L, 1L, 101, null, null));

        RefundDTO dto = refundDto(600L, 1000, "不想要了");
        // 03 卡 §4.4：CI-105 状态类错误为 409 ORDER_STATUS_ILLEGAL（R1-02 公共层再补 errorCode）
        CommonException ex = assertThrows(CommonException.class, () -> payService.refund(dto));
        assertEquals(409, ex.getCode());
        verify(refundMapper, never()).insert(any(Refund.class));
    }

    @Test
    @DisplayName("退款金额超过实付金额拒绝")
    void refund_amountExceeds_rejects() {
        when(orderClient.getPayInfo(700L)).thenReturn(payInfo(700L, 1L, 102, LocalDateTime.now(), "wx7"));

        RefundDTO dto = refundDto(700L, 99900, "不想要了");
        assertThrows(BadRequestException.class, () -> payService.refund(dto));
        verify(refundMapper, never()).insert(any(Refund.class));
    }

    @Test
    @DisplayName("已支付订单申请退款成功：生成退款单并通知订单服务")
    void refund_paidOrder_createsRefund() {
        when(orderClient.getPayInfo(800L)).thenReturn(payInfo(800L, 1L, 102, LocalDateTime.now(), "wx8"));
        java.util.concurrent.atomic.AtomicReference<Refund> captured = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(88L);
            captured.set(r);
            return 1;
        }).when(refundMapper).insert(any(Refund.class));

        RefundDTO dto = refundDto(800L, 9900, "七天无理由");
        RefundVO vo = payService.refund(dto);

        assertEquals(88L, vo.getRefundId());
        assertEquals("processing", vo.getStatus());
        verify(refundMapper).insert(any(Refund.class));
        assertEquals(800L, captured.get().getOrderId());
        assertEquals(1L, captured.get().getUserId());
        assertEquals(new BigDecimal("99.00"), captured.get().getAmount());
        // CI-105：资金侧直接置「退款成功」并落 refund_time（CX-06 ①）
        assertEquals(1, captured.get().getStatus());
        assertNotNull(captured.get().getRefundTime());
        // CI-105 收窄后不再回调订单服务（OrderClient.markRefunded 停用）
        verify(orderClient, never()).markRefunded(anyLong(), any());
    }

    @Test
    @DisplayName("支付结果状态映射：未付款 → unpaid")
    void queryResult_unpaid_mapsUnpaid() {
        when(orderClient.getPayInfo(900L)).thenReturn(payInfo(900L, 1L, 101, null, null));

        PayResultVO vo = payService.queryResult(900L);

        assertEquals("unpaid", vo.getStatus());
        assertNull(vo.getPayTime());
        assertNull(vo.getPayId());
    }

    @Test
    @DisplayName("支付结果状态映射：待发货/已收货 → paid")
    void queryResult_paid_mapsPaid() {
        LocalDateTime payTime = LocalDateTime.of(2026, 9, 25, 10, 0);
        when(orderClient.getPayInfo(910L)).thenReturn(payInfo(910L, 1L, 102, payTime, "wx910"));
        when(orderClient.getPayInfo(920L)).thenReturn(payInfo(920L, 1L, 301, payTime, "wx920"));

        PayResultVO vo1 = payService.queryResult(910L);
        PayResultVO vo2 = payService.queryResult(920L);

        assertEquals("paid", vo1.getStatus());
        assertEquals("2026-09-25 10:00:00", vo1.getPayTime());
        assertEquals("wx910", vo1.getPayId());
        assertEquals("paid", vo2.getStatus());
    }

    @Test
    @DisplayName("支付结果状态映射：售后 → refunded")
    void queryResult_refunded_mapsRefunded() {
        when(orderClient.getPayInfo(930L)).thenReturn(payInfo(930L, 1L, 401, null, "wx930"));

        PayResultVO vo = payService.queryResult(930L);

        assertEquals("refunded", vo.getStatus());
    }

    @Test
    @DisplayName("CI-103：mode=fail 只影响 payResult，不改订单状态")
    void wechatPay_modeFail_returnsFail() {
        when(orderClient.getPayInfo(410L)).thenReturn(payInfo(410L, 1L, 101, null, null));

        PayParamsVO vo = payService.wechatPay(410L, "fail");

        assertEquals(Boolean.TRUE, vo.getDemo());
        assertEquals("fail", vo.getPayResult());
        assertEquals("RSA", vo.getSignType());
        verify(orderClient, never()).markPaid(any());
    }

    @Test
    @DisplayName("CI-103：mode 缺省为 success")
    void wechatPay_modeDefault_success() {
        when(orderClient.getPayInfo(420L)).thenReturn(payInfo(420L, 1L, 101, null, null));

        PayParamsVO vo = payService.wechatPay(420L, null);

        assertEquals("success", vo.getPayResult());
        assertEquals(Boolean.TRUE, vo.getDemo());
    }

    @Test
    @DisplayName("CI-103：mode 非枚举值 → 400 PARAM_INVALID")
    void wechatPay_modeInvalid_rejects() {
        when(orderClient.getPayInfo(430L)).thenReturn(payInfo(430L, 1L, 101, null, null));

        assertThrows(BadRequestException.class, () -> payService.wechatPay(430L, "maybe"));
    }

    @Test
    @DisplayName("CI-106：出参带 result / orderStatus / orderStatusText")
    void queryResult_exposesResultAndStatusText() {
        LocalDateTime payTime = LocalDateTime.of(2026, 9, 25, 10, 0);
        when(orderClient.getPayInfo(950L)).thenReturn(payInfo(950L, 1L, 102, payTime, "wx950"));
        when(orderClient.getPayInfo(960L)).thenReturn(payInfo(960L, 1L, 103, null, null));

        PayResultVO paid = payService.queryResult(950L);
        PayResultVO closed = payService.queryResult(960L);

        assertEquals("success", paid.getResult());
        assertEquals(102, paid.getOrderStatus());
        assertEquals("待发货", paid.getOrderStatusText());
        assertNull(paid.getExpireTime());
        // 103 已关闭未支付：status 归 unpaid（不是 paid）、result 仍 pending
        assertEquals("unpaid", closed.getStatus());
        assertEquals("pending", closed.getResult());
        assertEquals("已关闭", closed.getOrderStatusText());
    }

    @Test
    @DisplayName("CN-IN-02：首次退款落 refund(status=1 + refund_time + aftersale_id)")
    void refundFunds_createsSucceededRefund() {
        when(orderClient.getPayInfo(1000L)).thenReturn(payInfo(1000L, 1L, 102, LocalDateTime.now(), "wx10"));
        java.util.concurrent.atomic.AtomicReference<Refund> captured = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(99L);
            captured.set(r);
            return 1;
        }).when(refundMapper).insert(any(Refund.class));

        PayRefundDTO dto = payRefundDto(5001L, 1000L, 8800, "退货退款");
        PayRefundResultDTO result = payService.refundFunds(dto);

        assertEquals(99L, result.getRefundId());
        assertEquals(1, result.getStatus());
        assertEquals("退款成功", result.getStatusText());
        assertNotNull(result.getRefundTime());
        assertEquals(5001L, captured.get().getAftersaleId());
        assertEquals(1000L, captured.get().getOrderId());
        assertEquals(new BigDecimal("88.00"), captured.get().getAmount());
        assertEquals(1, captured.get().getStatus());
        assertNotNull(captured.get().getRefundTime());
        // DR-17：CN-IN-02 不回调订单服务
        verify(orderClient, never()).markRefunded(anyLong(), any());
    }

    @Test
    @DisplayName("CN-IN-02：同 aftersaleId 重复调用返回同一 refundId 且不重复写库")
    void refundFunds_idempotent_returnsSame() {
        when(orderClient.getPayInfo(1001L)).thenReturn(payInfo(1001L, 1L, 102, null, "wx11"));
        Refund existed = new Refund()
                .setId(77L)
                .setAftersaleId(5002L)
                .setOrderId(1001L)
                .setAmount(new BigDecimal("10.00"))
                .setStatus(1)
                .setRefundTime(LocalDateTime.of(2026, 10, 1, 9, 0))
                .setDeleted(false);
        when(refundMapper.selectList(any())).thenReturn(java.util.List.of(existed));

        PayRefundResultDTO result = payService.refundFunds(payRefundDto(5002L, 1001L, 1000, "退货退款"));

        assertEquals(77L, result.getRefundId());
        assertEquals("2026-10-01 09:00:00", result.getRefundTime());
        verify(refundMapper, never()).insert(any(Refund.class));
    }

    @Test
    @DisplayName("CN-IN-02：同 aftersaleId 已有非成功行且金额不同 → 409 IDEMPOTENT_CONFLICT")
    void refundFunds_idempotentConflict() {
        when(orderClient.getPayInfo(1002L)).thenReturn(payInfo(1002L, 1L, 102, null, "wx12"));
        Refund pending = new Refund()
                .setId(78L)
                .setAftersaleId(5003L)
                .setOrderId(1002L)
                .setAmount(new BigDecimal("20.00"))
                .setStatus(0)
                .setDeleted(false);
        when(refundMapper.selectList(any())).thenReturn(java.util.List.of(pending));

        CommonException ex = assertThrows(CommonException.class,
                () -> payService.refundFunds(payRefundDto(5003L, 1002L, 1000, "退货退款")));

        assertEquals(409, ex.getCode());
        verify(refundMapper, never()).insert(any(Refund.class));
    }

    @Test
    @DisplayName("CN-IN-02：订单不存在 → 404")
    void refundFunds_orderNotExist() {
        when(orderClient.getPayInfo(1003L)).thenReturn(null);

        CommonException ex = assertThrows(CommonException.class,
                () -> payService.refundFunds(payRefundDto(5004L, 1003L, 1000, "退货退款")));

        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("CN-IN-02：金额≤0 或缺 aftersaleId → 400")
    void refundFunds_paramInvalid() {
        assertThrows(BadRequestException.class,
                () -> payService.refundFunds(payRefundDto(null, 1004L, 1000, "退货退款")));
        assertThrows(BadRequestException.class,
                () -> payService.refundFunds(payRefundDto(5005L, 1004L, 0, "退货退款")));
        verify(refundMapper, never()).insert(any(Refund.class));
    }

    private OrderPayInfoDTO payInfo(long orderId, long userId, int status,
                                    LocalDateTime payTime, String payId) {
        OrderPayInfoDTO info = new OrderPayInfoDTO();
        info.setOrderId(orderId);
        info.setUserId(userId);
        info.setOrderStatus(status);
        info.setActualPrice(new BigDecimal("99.00"));
        info.setPayTime(payTime);
        info.setPayId(payId);
        return info;
    }

    /**
     * CI-105 金额口径为整数分（P-27），helper 直接传分
     */
    private PayRefundDTO payRefundDto(Long aftersaleId, Long orderId, int amountCents, String reason) {
        PayRefundDTO dto = new PayRefundDTO();
        dto.setAftersaleId(aftersaleId);
        dto.setOrderId(orderId);
        dto.setAmount(amountCents);
        dto.setReason(reason);
        return dto;
    }

    private RefundDTO refundDto(long orderId, int amountCents, String reason) {
        RefundDTO dto = new RefundDTO();
        dto.setOrderId(orderId);
        dto.setAmount(amountCents);
        dto.setReason(reason);
        return dto;
    }
}

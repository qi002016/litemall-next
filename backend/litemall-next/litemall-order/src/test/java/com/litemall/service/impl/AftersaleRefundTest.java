package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.PayClient;
import com.litemall.constant.AftersaleStatus;
import com.litemall.domain.PO.Aftersale;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderGoods;
import com.litemall.domain.dto.AftersaleIdDTO;
import com.litemall.domain.dto.AftersaleRefundDTO;
import com.litemall.domain.dto.AftersaleRefundResultDTO;
import com.litemall.domain.dto.PayRefundDTO;
import com.litemall.domain.dto.PayRefundResultDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderMapper;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 售后退款编排（CN-07 / CN-08）与补偿一致性：
 * <ul>
 *   <li>两段式：① 本地置 refunding → ② pay 退款（幂等键 aftersaleId）→ ③ 回补 CAS → ④ 本地 completed；</li>
 *   <li>资金成功 + 回补失败 → <b>200 + restockPending=true</b>，停 refunding / restock_status=2，绝不 409；</li>
 *   <li>goods 不幂等，靠 {@code restock_status} CAS 0/2→1 收敛；CN-08 只处理 refunding + 2；</li>
 *   <li>券回退只在该订单整单全退（所有行 refund_status=2 且无未完结售后）时触发。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AftersaleRefundTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Aftersale.class);
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, OrderGoods.class);
    }

    private static final Long USER_ID = 1L;
    private static final Long AS_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long LINE_ID = 100L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderGoodsMapper orderGoodsMapper;
    @Mock
    private OrderLogMapper orderLogMapper;
    @Mock
    private AftersaleMapper aftersaleMapper;
    @Mock
    private PayClient payClient;
    @Mock
    private GoodsClient goodsClient;
    @Mock
    private MarketingClient marketingClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AftersaleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AftersaleServiceImpl(orderMapper, orderGoodsMapper, orderLogMapper, payClient,
                goodsClient, marketingClient, transactionTemplate);
        ReflectionTestUtils.setField(service, "baseMapper", aftersaleMapper);
        ReflectionTestUtils.setField(service, "entityClass", Aftersale.class);
        ReflectionTestUtils.setField(service, "configuredReturnAddress", "浙江省杭州市西湖区退货仓");
        UserContext.setUser(USER_ID);
        // 事务模板直接执行回调（单测无真实事务）
        lenient().doAnswer(inv -> {
            Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("CN-07：待收货直接退款（type=2、已发货）→ 回补 + completed + 行退款回写")
    void refundWithRestock_success() {
        Aftersale a = aftersale(AftersaleStatus.WAIT_RECEIVE, 2);
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(a);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(301, BigDecimal.ZERO));
        when(orderGoodsMapper.selectById(LINE_ID)).thenReturn(line(1, 0));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(payClient.refund(any(PayRefundDTO.class))).thenReturn(payOk());

        AftersaleRefundResultDTO result = service.refund(refundDTO(null));

        ArgumentCaptor<PayRefundDTO> payCaptor = ArgumentCaptor.forClass(PayRefundDTO.class);
        verify(payClient).refund(payCaptor.capture());
        assertThat(payCaptor.getValue().getAftersaleId()).isEqualTo(AS_ID);
        assertThat(payCaptor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(payCaptor.getValue().getAmount()).isEqualTo(1000);

        ArgumentCaptor<StockChangeDTO> stockCaptor = ArgumentCaptor.forClass(StockChangeDTO.class);
        verify(goodsClient).releaseStockBatch(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getItems()).hasSize(1);
        assertThat(stockCaptor.getValue().getItems().get(0).getProductId()).isEqualTo(500L);
        assertThat(stockCaptor.getValue().getItems().get(0).getNumber()).isEqualTo(1);

        // CAS：2→3、restock 0→1、3→4 共三次，行回写一次
        verify(aftersaleMapper, times(3)).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(orderGoodsMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        assertThat(result.getRestockPending()).isFalse();
        assertThat(result.getRefundAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("CN-07：资金已退但回补失败 → 200 + restockPending=true，状态停 refunding 不抛 409")
    void refundRestockFail_pendingNotConflict() {
        Aftersale a = aftersale(AftersaleStatus.WAIT_RECEIVE, 2);
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(a);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(301, BigDecimal.ZERO));
        when(orderGoodsMapper.selectById(LINE_ID)).thenReturn(line(1, 0));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(payClient.refund(any(PayRefundDTO.class))).thenReturn(payOk());
        org.mockito.Mockito.doThrow(new RuntimeException("goods down"))
                .when(goodsClient).releaseStockBatch(any(StockChangeDTO.class));

        AftersaleRefundResultDTO result = service.refund(refundDTO(null));

        assertThat(result.getRestockPending()).isTrue();
        // 未进入本地完成事务（不加 completed，不写行）
        verify(transactionTemplate, never()).executeWithoutResult(any());
        verify(orderGoodsMapper, never()).update(any(), any());
        verify(marketingClient, never()).rollbackCoupon(any());
    }

    @Test
    @DisplayName("CN-07：已退款完成再退 → 409 AFTERSALE_STATUS_ILLEGAL（且不调 pay）")
    void refundCompleted_conflict() {
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(aftersale(AftersaleStatus.COMPLETED, 2));

        assertThatThrownBy(() -> service.refund(refundDTO(null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已退款完成")
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("AFTERSALE_STATUS_ILLEGAL");
        verify(payClient, never()).refund(any());
    }

    @Test
    @DisplayName("CN-07：申请中/待寄回不能直接退款 → 409")
    void refundBeforeApproved_conflict() {
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(aftersale(AftersaleStatus.APPLIED, 1));

        assertThatThrownBy(() -> service.refund(refundDTO(null)))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("AFTERSALE_STATUS_ILLEGAL");
        verify(payClient, never()).refund(any());
    }

    @Test
    @DisplayName("CN-07：amount 与售后单不一致 → 400 REFUND_AMOUNT_MISMATCH")
    void refundAmountMismatch_badRequest() {
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(aftersale(AftersaleStatus.REFUNDING, 2));

        assertThatThrownBy(() -> service.refund(refundDTO(999)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorCode())
                .isEqualTo("REFUND_AMOUNT_MISMATCH");
        verify(payClient, never()).refund(any());
    }

    @Test
    @DisplayName("CN-07：矩阵④ 已发货仅退款 → 无需回补，不调 goods 但 restock_status 仍置 1")
    void refundRefundOnly_noRestock() {
        Aftersale a = aftersale(AftersaleStatus.REFUNDING, 1);
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(a);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(301, BigDecimal.ZERO));
        when(orderGoodsMapper.selectById(LINE_ID)).thenReturn(line(1, 0));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(payClient.refund(any(PayRefundDTO.class))).thenReturn(payOk());

        AftersaleRefundResultDTO result = service.refund(refundDTO(null));

        verify(goodsClient, never()).releaseStockBatch(any());
        // restock 置 1 + 3→4 CAS
        verify(aftersaleMapper, times(2)).update(isNull(), any(LambdaUpdateWrapper.class));
        assertThat(result.getRestockPending()).isFalse();
    }

    @Test
    @DisplayName("CN-08：refunding + restock=2 重试成功 → 补回 + completed")
    void retryRestock_success() {
        Aftersale a = aftersale(AftersaleStatus.REFUNDING, 2);
        a.setRestockStatus(AftersaleStatus.RESTOCK_PENDING);
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(a);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(301, BigDecimal.ZERO));
        when(orderGoodsMapper.selectById(LINE_ID)).thenReturn(line(1, 0));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AftersaleIdDTO dto = new AftersaleIdDTO();
        dto.setAftersaleId(AS_ID);
        dto.setAdminId(9L);
        AftersaleRefundResultDTO result = service.retryRestock(dto);

        verify(goodsClient).releaseStockBatch(any(StockChangeDTO.class));
        verify(payClient, never()).refund(any());
        assertThat(result.getRestockPending()).isFalse();
    }

    @Test
    @DisplayName("CN-08：状态不是 refunding+2 → 409（无需重试）")
    void retryRestock_illegalState_conflict() {
        Aftersale notPending = aftersale(AftersaleStatus.REFUNDING, 2);
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(notPending);

        AftersaleIdDTO dto = new AftersaleIdDTO();
        dto.setAftersaleId(AS_ID);

        assertThatThrownBy(() -> service.retryRestock(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("无需重试回补")
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("AFTERSALE_STATUS_ILLEGAL");
        verify(goodsClient, never()).releaseStockBatch(any());
    }

    @Test
    @DisplayName("D5：整单全退（所有行 refund_status=2 且无未完结）→ 回退优惠券")
    void rollbackCoupon_whenOrderFullyRefunded() {
        Aftersale a = aftersale(AftersaleStatus.REFUNDING, 1);
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(a);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(301, new BigDecimal("5.00")));
        when(orderGoodsMapper.selectById(LINE_ID)).thenReturn(line(1, 0));
        when(orderGoodsMapper.selectList(any())).thenReturn(List.of(line(1, 2)));
        when(aftersaleMapper.selectCount(any())).thenReturn(0L);
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(payClient.refund(any(PayRefundDTO.class))).thenReturn(payOk());

        service.refund(refundDTO(null));

        verify(marketingClient).rollbackCoupon(any());
    }

    @Test
    @DisplayName("D5：还有行未全退 → 不回退优惠券")
    void rollbackCoupon_skippedWhenPartiallyRefunded() {
        Aftersale a = aftersale(AftersaleStatus.REFUNDING, 1);
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(a);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(301, new BigDecimal("5.00")));
        when(orderGoodsMapper.selectById(LINE_ID)).thenReturn(line(1, 0));
        when(orderGoodsMapper.selectList(any())).thenReturn(List.of(line(1, 1)));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(payClient.refund(any(PayRefundDTO.class))).thenReturn(payOk());

        service.refund(refundDTO(null));

        verify(marketingClient, never()).rollbackCoupon(any());
    }

    @Test
    @DisplayName("pay 抛异常 → 保持 refunding 待重试，不写 completed、不回补")
    void refundPayFailure_keepsRefunding() {
        when(aftersaleMapper.selectById(AS_ID)).thenReturn(aftersale(AftersaleStatus.REFUNDING, 2));
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(301, BigDecimal.ZERO));
        when(orderGoodsMapper.selectById(LINE_ID)).thenReturn(line(1, 0));
        when(payClient.refund(any(PayRefundDTO.class))).thenThrow(new RuntimeException("pay down"));

        assertThatThrownBy(() -> service.refund(refundDTO(null)))
                .isInstanceOf(com.litemall.exception.BizIllegalException.class);
        verify(goodsClient, never()).releaseStockBatch(any());
        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    /* ==================== 工厂 ==================== */

    private AftersaleRefundDTO refundDTO(Integer amount) {
        AftersaleRefundDTO dto = new AftersaleRefundDTO();
        dto.setAftersaleId(AS_ID);
        dto.setAmount(amount);
        dto.setRemark("同意退款");
        dto.setAdminId(9L);
        return dto;
    }

    private PayRefundResultDTO payOk() {
        PayRefundResultDTO dto = new PayRefundResultDTO();
        dto.setRefundId(77L);
        dto.setStatus(1);
        dto.setStatusText("退款成功");
        dto.setRefundTime("2026-11-01 12:00:00");
        return dto;
    }

    private Aftersale aftersale(Integer status, Integer type) {
        Aftersale a = new Aftersale();
        a.setId(AS_ID);
        a.setAftersaleSn("AS20261101000001");
        a.setOrderId(ORDER_ID);
        a.setOrderGoodsId(LINE_ID);
        a.setUserId(USER_ID);
        a.setType(type);
        a.setNumber(1);
        a.setReason("商品有瑕疵");
        a.setAmount(new BigDecimal("10.00"));
        a.setStatus(status);
        a.setRestockStatus(AftersaleStatus.RESTOCK_NONE);
        a.setAddTime(LocalDateTime.now().minusDays(1));
        a.setUpdateTime(LocalDateTime.now());
        a.setDeleted(false);
        return a;
    }

    private Order order(Integer status, BigDecimal couponPrice) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderSn("SN10");
        order.setUserId(USER_ID);
        order.setOrderStatus(status);
        order.setActualPrice(new BigDecimal("10.00"));
        order.setCouponPrice(couponPrice);
        order.setDeleted(false);
        return order;
    }

    private OrderGoods line(Integer number, Integer refundStatus) {
        OrderGoods og = new OrderGoods();
        og.setId(LINE_ID);
        og.setOrderId(ORDER_ID);
        og.setGoodsName("测试商品");
        og.setProductId(500L);
        og.setNumber(number);
        og.setRefundedNumber(refundStatus != null && refundStatus == 2 ? number : 0);
        og.setRefundStatus(refundStatus);
        og.setActualAmount(new BigDecimal("10.00"));
        og.setPrice(new BigDecimal("10.00"));
        og.setDeleted(false);
        return og;
    }
}
package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderGoods;
import com.litemall.domain.dto.DeductBatchResultDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import com.litemall.domain.dto.PaySuccessResultDTO;
import com.litemall.exception.ConflictException;
import com.litemall.exception.NotFoundException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderMapper;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 端状态机（CI-85 取消 / CI-86 确认收货 / CN-20 删除）与 CI-97 支付回调闸门。
 * <p>核心口径：全部 CAS，affected=0 → 409；他人订单 → 404；重复隐藏 → 200 幂等。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderStateMachineTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private OrderGoodsMapper orderGoodsMapper;
    @Mock
    private AftersaleMapper aftersaleMapper;
    @Mock
    private OrderLogMapper orderLogMapper;
    @Mock
    private GoodsClient goodsClient;
    @Mock
    private UserClient userClient;
    @Mock
    private MarketingClient marketingClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(cartMapper, orderGoodsMapper, aftersaleMapper, orderLogMapper,
                goodsClient, userClient, marketingClient, stringRedisTemplate);
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "entityClass", Order.class);
        UserContext.setUser(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /* ==================== CI-85 取消 ==================== */

    @Test
    @DisplayName("CI-85：非未付款订单取消 → 409 ORDER_STATUS_ILLEGAL")
    void cancelNonUnpaid_conflict() {
        when(orderMapper.selectOne(any())).thenReturn(order(1L, 102));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("该订单当前状态不可取消");
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("CI-85：他人订单 → 404（不用 403 泄露存在性）")
    void cancelForeignOrder_notFound() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> orderService.cancelOrder(2L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("订单不存在");
    }

    /* ==================== CI-86 确认收货 ==================== */

    @Test
    @DisplayName("CI-86：201 → 301，CAS 同时写 confirm_time")
    void confirmReceive_success() {
        when(orderMapper.selectOne(any())).thenReturn(order(3L, 201));
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        orderService.confirmReceive(3L);

        verify(orderMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("CI-86：非 201 → 409「只有已发货订单可以确认收货」")
    void confirmReceiveWrongStatus_conflict() {
        when(orderMapper.selectOne(any())).thenReturn(order(4L, 102));

        assertThatThrownBy(() -> orderService.confirmReceive(4L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("只有已发货订单可以确认收货");
    }

    /* ==================== CN-20 删除订单 ==================== */

    @Test
    @DisplayName("CN-20：重复删除返回 200，不再写库")
    void hideAlreadyHidden_idempotent() {
        when(orderMapper.selectOne(any())).thenReturn(order(5L, 103).setUserDeleted(true));

        orderService.hideOrder(5L);

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("CN-20：存在未完结售后 → 409 AFTERSALE_EXISTS")
    void hideWithUnfinishedAftersale_conflict() {
        when(orderMapper.selectOne(any())).thenReturn(order(6L, 301));
        when(aftersaleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> orderService.hideOrder(6L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("有处理中的售后");
    }

    @Test
    @DisplayName("CN-20：首次删除置 user_deleted=1；并发已删（affected=0）仍按 200 处理")
    void hideFirstTime_success() {
        when(orderMapper.selectOne(any())).thenReturn(order(7L, 103));
        when(aftersaleMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        orderService.hideOrder(7L);

        verify(orderMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    /* ==================== CI-97 支付回调闸门 ==================== */

    @Test
    @DisplayName("CI-97：订单不存在 → 404 ORDER_NOT_FOUND")
    void markPaidMissingOrder_notFound() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> orderService.markPaid(paySuccess(8L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("订单不存在");
    }

    @Test
    @DisplayName("CI-97：pay_time 已写 → applied=false，不再调 goods（同单只实扣一次）")
    void markPaidAlreadyPaid_noop() {
        Order paid = order(9L, 102).setPayTime(LocalDateTime.now().minusMinutes(1)).setPayId("1");
        when(orderMapper.selectOne(any())).thenReturn(paid);

        PaySuccessResultDTO result = orderService.markPaid(paySuccess(9L));

        assertThat(result.isApplied()).isFalse();
        assertThat(result.isDeducted()).isFalse();
        verify(goodsClient, never()).deductStockBatch(any());
    }

    @Test
    @DisplayName("CI-97：已关闭订单（DR-11）→ 200 且不扣库存、不改状态")
    void markPaidCanceledOrder_keepsClosed() {
        when(orderMapper.selectOne(any())).thenReturn(order(10L, 103));

        PaySuccessResultDTO result = orderService.markPaid(paySuccess(10L));

        assertThat(result.isApplied()).isFalse();
        verify(orderMapper, never()).update(any(), any());
        verify(goodsClient, never()).deductStockBatch(any());
    }

    @Test
    @DisplayName("CI-97：实扣前库存不足 → 200 + shortage，订单保持 101（P-31，禁止 409）")
    void markPaidStockShortage_staysUnpaid() {
        when(orderMapper.selectOne(any())).thenReturn(order(11L, 101));
        when(orderGoodsMapper.selectList(any())).thenReturn(List.of(
                new OrderGoods().setId(1L).setOrderId(11L).setProductId(1001L).setNumber(3)));
        when(goodsClient.getProductInfo(1001L)).thenReturn(product(1, 1));

        PaySuccessResultDTO result = orderService.markPaid(paySuccess(11L));

        assertThat(result.isApplied()).isFalse();
        assertThat(result.isDeducted()).isFalse();
        assertThat(result.getShortage()).hasSize(1);
        assertThat(result.getShortage().get(0).getProductId()).isEqualTo(1001L);
        assertThat(result.getShortage().get(0).getNeed()).isEqualTo(3);
        assertThat(result.getShortage().get(0).getAvailable()).isEqualTo(1);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("CI-97：库存充足 → 101→102 + 一次性实扣，返回 applied=true/deducted=true")
    void markPaidSuccess_deductsOnce() {
        when(orderMapper.selectOne(any())).thenReturn(order(12L, 101));
        when(orderGoodsMapper.selectList(any())).thenReturn(List.of(
                new OrderGoods().setId(1L).setOrderId(12L).setProductId(1002L).setNumber(2)));
        when(goodsClient.getProductInfo(1002L)).thenReturn(product(10, 10));
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(goodsClient.deductStockBatch(any())).thenReturn(deductResult(true));

        PaySuccessResultDTO result = orderService.markPaid(paySuccess(12L));

        assertThat(result.isApplied()).isTrue();
        assertThat(result.isDeducted()).isTrue();
        verify(goodsClient).deductStockBatch(any());
    }

    @Test
    @DisplayName("CI-97：实扣返回失败 → 200 + deducted=false，订单已 102（记残余风险，不自动退款）")
    void markPaidDeductFailed_reportsFalse() {
        when(orderMapper.selectOne(any())).thenReturn(order(13L, 101));
        when(orderGoodsMapper.selectList(any())).thenReturn(List.of(
                new OrderGoods().setId(1L).setOrderId(13L).setProductId(1003L).setNumber(1)));
        when(goodsClient.getProductInfo(1003L)).thenReturn(product(5, 5));
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(goodsClient.deductStockBatch(any())).thenReturn(deductResult(false));

        PaySuccessResultDTO result = orderService.markPaid(paySuccess(13L));

        assertThat(result.isApplied()).isTrue();
        assertThat(result.isDeducted()).isFalse();
    }

    @Test
    @DisplayName("CI-97：并发下 CAS 失败（已被其他回调处理）→ applied=false，不重复扣库存")
    void markPaidCasMissed_noDeduct() {
        when(orderMapper.selectOne(any())).thenReturn(order(14L, 101));
        when(orderGoodsMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        PaySuccessResultDTO result = orderService.markPaid(paySuccess(14L));

        assertThat(result.isApplied()).isFalse();
        verify(goodsClient, never()).deductStockBatch(any());
    }

    /* ==================== CN-IN-01 评价校验 ==================== */

    @Test
    @DisplayName("CN-IN-01：301 且未退款未评价 → canComment=true")
    void orderGoodsStatus_canComment() {
        when(orderGoodsMapper.selectOne(any())).thenReturn(new OrderGoods()
                .setId(1L).setOrderId(20L).setNumber(1).setComment(0).setRefundStatus(0).setRefundedNumber(0));
        when(orderMapper.selectOne(any())).thenReturn(order(20L, 301));

        var dto = orderService.orderGoodsStatus(1L, 1L);

        assertThat(dto.getCanComment()).isTrue();
        assertThat(dto.getOrderStatusText()).isEqualTo("已收货");
    }

    @Test
    @DisplayName("CN-IN-01：已评价 → canComment=false 且返回 commentId")
    void orderGoodsStatus_commented() {
        when(orderGoodsMapper.selectOne(any())).thenReturn(new OrderGoods()
                .setId(2L).setOrderId(21L).setNumber(1).setComment(77).setRefundStatus(0).setRefundedNumber(0));
        when(orderMapper.selectOne(any())).thenReturn(order(21L, 301));

        var dto = orderService.orderGoodsStatus(2L, 1L);

        assertThat(dto.getCanComment()).isFalse();
        assertThat(dto.getCommented()).isTrue();
        assertThat(dto.getCommentId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("CN-IN-01：userId 不匹配 → 404（不返 403）")
    void orderGoodsStatus_userMismatch_notFound() {
        when(orderGoodsMapper.selectOne(any())).thenReturn(new OrderGoods()
                .setId(3L).setOrderId(22L).setNumber(1).setComment(0).setRefundStatus(0).setRefundedNumber(0));
        when(orderMapper.selectOne(any())).thenReturn(order(22L, 301));

        assertThatThrownBy(() -> orderService.orderGoodsStatus(3L, 999L))
                .isInstanceOf(NotFoundException.class);
    }

    /* ==================== 辅助 ==================== */

    private Order order(Long id, int status) {
        return new Order()
                .setId(id)
                .setOrderSn("SN" + id)
                .setUserId(1L)
                .setOrderStatus(status)
                .setDeleted(false);
    }

    private PaySuccessDTO paySuccess(Long orderId) {
        PaySuccessDTO dto = new PaySuccessDTO();
        dto.setOrderId(orderId);
        dto.setPayId("123456");
        return dto;
    }

    /** litemall-api 的 DTO 是纯 @Data（无链式 setter），统一用工厂方法构造。 */
    private GoodsProductInfoDTO product(Integer number, Integer sellable) {
        GoodsProductInfoDTO dto = new GoodsProductInfoDTO();
        dto.setNumber(number);
        dto.setSellable(sellable);
        return dto;
    }

    private DeductBatchResultDTO deductResult(boolean deducted) {
        DeductBatchResultDTO dto = new DeductBatchResultDTO();
        dto.setDeducted(deducted);
        return dto;
    }
}
package com.litemall.service.impl;

import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderGoods;
import com.litemall.domain.dto.OrderRefundDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H5：管理端订单状态机单元测试。
 * <p>
 * 覆盖 admin 侧发货/退款经由 order 服务后的状态校验：
 * <ul>
 *   <li>非法流转被拒：未付款订单发货、已取消订单退款、未付款订单退款、已退款完成再退款；</li>
 *   <li>合法流转成功：102 待发货 → 201 已发货；102 待发货 → 401 售后，
 *       且复用既有的批量库存回补与优惠券回退；</li>
 *   <li>401 幂等：重复退款不重复补偿库存。</li>
 * </ul>
 * 说明：{@code OrderServiceImpl} 继承 MyBatis-Plus 的 {@code ServiceImpl}，
 * 其 {@code baseMapper} / {@code entityClass} 由 Spring 注入，这里用 {@link ReflectionTestUtils} 手工补上。
 */
@ExtendWith(MockitoExtension.class)
class OrderAdminStateMachineTest {

    private static final int STATUS_UNPAID = 101;
    private static final int STATUS_UNDELIVERED = 102;
    private static final int STATUS_CANCELED = 103;
    private static final int STATUS_SHIPPED = 201;
    private static final int STATUS_REFUND = 401;
    private static final int STATUS_REFUND_DONE = 402;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private OrderGoodsMapper orderGoodsMapper;
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
        orderService = new OrderServiceImpl(cartMapper, orderGoodsMapper, goodsClient,
                userClient, marketingClient, stringRedisTemplate);
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "entityClass", Order.class);
    }

    /* ==================== 发货 ==================== */

    @Test
    @DisplayName("H5/状态机：未付款订单发货被拒，且不产生任何订单写入")
    void shipUnpaidOrder_rejected() {
        when(orderMapper.selectById(1L)).thenReturn(order(1, STATUS_UNPAID));

        assertThatThrownBy(() -> orderService.adminShip(shipDTO(1L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("仅待发货订单可发货");

        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("H5/状态机：已取消订单发货被拒")
    void shipCanceledOrder_rejected() {
        when(orderMapper.selectById(2L)).thenReturn(order(2, STATUS_CANCELED));

        assertThatThrownBy(() -> orderService.adminShip(shipDTO(2L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("仅待发货订单可发货");

        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("H5/状态机：102 待发货 → 201 已发货，并写入快递信息与发货时间")
    void shipUndeliveredOrder_success() {
        when(orderMapper.selectById(3L)).thenReturn(order(3, STATUS_UNDELIVERED));

        OrderShipDTO dto = shipDTO(3L);
        dto.setShipChannel("SF");
        dto.setShipSn("SF123456");
        orderService.adminShip(dto);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(captor.capture());
        Order updated = captor.getValue();
        assertThat(updated.getOrderStatus()).isEqualTo(STATUS_SHIPPED);
        assertThat(updated.getShipChannel()).isEqualTo("SF");
        assertThat(updated.getShipSn()).isEqualTo("SF123456");
        assertThat(updated.getShipTime()).isNotNull();
    }

    /* ==================== 退款 ==================== */

    @Test
    @DisplayName("H5/状态机：未付款订单退款被拒（应走取消订单）")
    void refundUnpaidOrder_rejected() {
        when(orderMapper.selectById(11L)).thenReturn(order(11, STATUS_UNPAID));

        assertThatThrownBy(() -> orderService.adminRefund(refundDTO(11L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("未付款订单无需退款");

        verify(goodsClient, never()).releaseStockBatch(any());
        verify(marketingClient, never()).rollbackCoupon(any());
    }

    @Test
    @DisplayName("H5/状态机：已取消订单退款被拒，且不回补库存/不回退券")
    void refundCanceledOrder_rejected() {
        when(orderMapper.selectById(12L)).thenReturn(order(12, STATUS_CANCELED));

        assertThatThrownBy(() -> orderService.adminRefund(refundDTO(12L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("订单已取消，不可退款");

        verify(goodsClient, never()).releaseStockBatch(any());
        verify(marketingClient, never()).rollbackCoupon(any());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("H5/状态机：已退款完成（402）重复退款被拒")
    void refundDoneOrder_rejected() {
        when(orderMapper.selectById(13L)).thenReturn(order(13, STATUS_REFUND_DONE));

        assertThatThrownBy(() -> orderService.adminRefund(refundDTO(13L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("订单已退款完成");
    }

    @Test
    @DisplayName("H5/状态机：102 待发货 → 401 售后，并复用批量库存回补 + 优惠券回退")
    void refundUndeliveredOrder_compensatesStockAndCoupon() {
        Order order = order(14, STATUS_UNDELIVERED);
        order.setActualPrice(new BigDecimal("88.00"));
        when(orderMapper.selectById(14L)).thenReturn(order);
        when(orderGoodsMapper.selectList(any())).thenReturn(List.of(
                new OrderGoods().setOrderId(14L).setProductId(1001L).setNumber(2),
                new OrderGoods().setOrderId(14L).setProductId(1002L).setNumber(1)));

        OrderRefundDTO dto = refundDTO(14L);
        dto.setRefundAmount(new BigDecimal("88.00"));
        dto.setRefundContent("管理端协商退款");
        orderService.adminRefund(dto);

        // 库存回补：一次批量 RPC，明细与订单商品一致
        ArgumentCaptor<StockChangeDTO> stockCaptor = ArgumentCaptor.forClass(StockChangeDTO.class);
        verify(goodsClient).releaseStockBatch(stockCaptor.capture());
        List<StockChangeDTO.Item> items = stockCaptor.getValue().getItems();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getProductId()).isEqualTo(1001L);
        assertThat(items.get(0).getNumber()).isEqualTo(2);
        assertThat(items.get(1).getProductId()).isEqualTo(1002L);

        // 优惠券回退：与取消订单同一调用形态（仅传 orderId，由 marketing 服务反查）
        verify(marketingClient).rollbackCoupon(any());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        Order updated = orderCaptor.getValue();
        assertThat(updated.getOrderStatus()).isEqualTo(STATUS_REFUND);
        assertThat(updated.getRefundAmount()).isEqualByComparingTo("88.00");
        assertThat(updated.getRefundType()).isEqualTo("admin");
        assertThat(updated.getRefundTime()).isNotNull();
    }

    @Test
    @DisplayName("H5/状态机：401 售后中重复退款为幂等（不再写库、不再补偿）")
    void refundRefundingOrder_isIdempotent() {
        when(orderMapper.selectById(15L)).thenReturn(order(15, STATUS_REFUND));

        orderService.adminRefund(refundDTO(15L));

        verify(orderMapper, never()).updateById(any(Order.class));
        verify(goodsClient, never()).releaseStockBatch(any());
        verify(marketingClient, never()).rollbackCoupon(any());
    }

    @Test
    @DisplayName("H5/状态机：订单不存在时拒绝")
    void refundMissingOrder_rejected() {
        when(orderMapper.selectById(16L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.adminRefund(refundDTO(16L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("订单不存在");
    }

    /* ==================== 辅助方法 ==================== */

    private Order order(long id, int status) {
        return new Order()
                .setId(id)
                .setOrderSn("SN" + id)
                .setUserId(1L)
                .setOrderStatus(status)
                .setDeleted(false);
    }

    private OrderShipDTO shipDTO(Long orderId) {
        OrderShipDTO dto = new OrderShipDTO();
        dto.setOrderId(orderId);
        return dto;
    }

    private OrderRefundDTO refundDTO(Long orderId) {
        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderId(orderId);
        return dto;
    }
}
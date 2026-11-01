package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.domain.PO.Aftersale;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderGoods;
import com.litemall.domain.PO.OrderLog;
import com.litemall.constant.AftersaleStatus;
import com.litemall.constant.OrderStatus;
import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.domain.dto.OrderAdminDetailDTO;
import com.litemall.domain.dto.OrderCloseDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.exception.NotFoundException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderStatsMapper;
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
 * 管理端订单状态机（CI-27 发货 / CN-10 关单 / CN-11 超时关单 / CN-12 改物流）。
 * <p>全部走 CAS：{@code lambdaUpdate().eq(status).update()}，affected=0 → 409（03 §5.6）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderAdminStateMachineTest {

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
    private OrderStatsMapper orderStatsMapper;
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
        orderService = new OrderServiceImpl(cartMapper, orderGoodsMapper, aftersaleMapper, orderLogMapper, orderStatsMapper,
                goodsClient, userClient, marketingClient, stringRedisTemplate);
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "entityClass", Order.class);
    }

    /* ==================== CN-33 订单详情行级售后摘要 ==================== */

    @Test
    @DisplayName("CN-33：行级 aftersaleId/aftersaleStatus 取该行未完结售后，status 出字符串标识")
    void detail_lineAftersaleSummary_ident() {
        Order order = new Order();
        order.setId(25L);
        order.setOrderSn("SN25");
        order.setUserId(1L);
        order.setOrderStatus(OrderStatus.UNDELIVERED);
        order.setGoodsPrice(new BigDecimal("59.90"));
        order.setFreightPrice(BigDecimal.ZERO);
        order.setCouponPrice(BigDecimal.ZERO);
        order.setOrderPrice(new BigDecimal("59.90"));
        order.setActualPrice(new BigDecimal("59.90"));
        order.setDeleted(false);
        when(orderMapper.selectOne(any())).thenReturn(order);

        OrderGoods og = new OrderGoods();
        og.setId(26L);
        og.setOrderId(25L);
        og.setNumber(1);
        og.setRefundedNumber(0);
        og.setRefundStatus(0);
        og.setDeleted(false);
        when(orderGoodsMapper.selectList(any())).thenReturn(List.of(og));

        Aftersale a = new Aftersale();
        a.setId(2L);
        a.setOrderId(25L);
        a.setOrderGoodsId(26L);
        a.setStatus(AftersaleStatus.APPLIED);
        a.setType(AftersaleStatus.TYPE_REFUND_ONLY);
        a.setDeleted(false);
        when(aftersaleMapper.selectList(any())).thenReturn(List.of(a));
        when(orderLogMapper.selectCount(any())).thenReturn(0L);

        OrderAdminDetailDTO dto = orderService.adminOrderDetail(25L);

        OrderAdminDetailDTO.Line line = dto.getLines().get(0);
        assertThat(line.getAftersaleId()).isEqualTo(2L);
        assertThat(line.getAftersaleStatus()).isEqualTo("applied");
        assertThat(line.getAftersaleStatusText()).isEqualTo("申请中");
    }

    /* ==================== CI-27 发货 ==================== */

    @Test
    @DisplayName("CI-27：未付款订单发货 → CAS(102) 不命中 → 409，且不留发货日志")
    void shipUnpaidOrder_conflict() {
        when(orderMapper.selectOne(any())).thenReturn(order(1L, 101));
        stubCas(0);

        assertThatThrownBy(() -> orderService.adminShip(shipDTO(1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("只有待发货订单可以发货");
        verify(orderLogMapper, never()).insert(any(OrderLog.class));
    }

    @Test
    @DisplayName("CI-27：已关闭订单发货 → 409（BR-ORD-06）")
    void shipCanceledOrder_conflict() {
        when(orderMapper.selectOne(any())).thenReturn(order(2L, 103));
        stubCas(0);

        assertThatThrownBy(() -> orderService.adminShip(shipDTO(2L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("只有待发货订单可以发货");
    }

    @Test
    @DisplayName("CI-27：102 待发货 → 201 已发货，CAS 写入快递信息与发货时间")
    void shipUndeliveredOrder_success() {
        when(orderMapper.selectOne(any())).thenReturn(order(3L, 102));
        stubCas(1);

        orderService.adminShip(shipDTO(3L));

        LambdaUpdateWrapper<Order> wrapper = captureCasWrapper();
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(201, "SF", "SF123456");
    }

    @Test
    @DisplayName("CI-27：并发已被改状态（CAS affected=0）→ 409，不静默成功")
    void shipCasMissed_conflict() {
        when(orderMapper.selectOne(any())).thenReturn(order(4L, 102));
        stubCas(0);

        assertThatThrownBy(() -> orderService.adminShip(shipDTO(4L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("只有待发货订单可以发货");
    }

    @Test
    @DisplayName("CI-27：快递公司/单号为空 → 400（P-18 服务内校验）")
    void shipBlankFields_rejected() {
        when(orderMapper.selectOne(any())).thenReturn(order(5L, 102));

        OrderShipDTO dto = shipDTO(5L);
        dto.setShipChannel("  ");
        dto.setShipSn("SF1");

        assertThatThrownBy(() -> orderService.adminShip(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("快递公司不能为空");
    }

    /* ==================== CN-10 运营关单 ==================== */

    @Test
    @DisplayName("CN-10：101 → 103，写 close_type=3 + end_time，并留痕 + 回退优惠券")
    void adminCloseUnpaid_success() {
        Order order = order(11L, 101);
        order.setCouponPrice(new BigDecimal("5.00"));
        when(orderMapper.selectOne(any())).thenReturn(order);
        stubCas(1);

        OrderCloseDTO dto = new OrderCloseDTO();
        dto.setOrderId(11L);
        dto.setReason("运营核实为恶意占单");
        dto.setAdminId(9001L);
        orderService.adminClose(dto);

        LambdaUpdateWrapper<Order> wrapper = captureCasWrapper();
        assertThat(wrapper.getParamNameValuePairs().values()).contains(103, 3);
        // 未支付过：只回退券，不回补库存
        verify(marketingClient).rollbackCoupon(any());
        verify(goodsClient, never()).releaseStockBatch(any());
        // 留痕：admin 列存 adminId 字符串
        ArgumentCaptor<OrderLog> logCaptor = ArgumentCaptor.forClass(OrderLog.class);
        verify(orderLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("order:close");
        assertThat(logCaptor.getValue().getAdmin()).isEqualTo("9001");
        assertThat(logCaptor.getValue().getTargetId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("CN-10：非未付款订单关单 → 409「该订单当前状态不可关闭」")
    void adminCloseNonUnpaid_conflict() {
        when(orderMapper.selectOne(any())).thenReturn(order(12L, 102));
        stubCas(0);

        OrderCloseDTO dto = new OrderCloseDTO();
        dto.setOrderId(12L);

        assertThatThrownBy(() -> orderService.adminClose(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("该订单当前状态不可关闭");
        verify(marketingClient, never()).rollbackCoupon(any());
        verify(orderLogMapper, never()).insert(any(OrderLog.class));
    }

    @Test
    @DisplayName("CN-10：关单原因超过 255 字 → 400")
    void adminCloseReasonTooLong_rejected() {
        when(orderMapper.selectOne(any())).thenReturn(order(13L, 101));
        OrderCloseDTO dto = new OrderCloseDTO();
        dto.setOrderId(13L);
        dto.setReason("x".repeat(256));

        assertThatThrownBy(() -> orderService.adminClose(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("关单原因不能超过 255 字");
    }

    @Test
    @DisplayName("CN-10：订单不存在 → 404 ORDER_NOT_FOUND")
    void adminCloseMissingOrder_notFound() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        OrderCloseDTO dto = new OrderCloseDTO();
        dto.setOrderId(14L);

        assertThatThrownBy(() -> orderService.adminClose(dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("订单不存在");
    }

    /* ==================== CN-11 超时关单 ==================== */

    @Test
    @DisplayName("CN-11：limit 越界 → 400")
    void closeExpiredLimitInvalid_rejected() {
        assertThatThrownBy(() -> orderService.adminCloseExpired(0, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit 取值范围 1–200");
        assertThatThrownBy(() -> orderService.adminCloseExpired(201, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit 取值范围 1–200");
    }

    @Test
    @DisplayName("CN-11：无超时订单 → total=0，不 404")
    void closeExpiredNoCandidate_empty() {
        when(orderMapper.selectList(any())).thenReturn(List.of());

        CloseExpiredResultDTO result = orderService.adminCloseExpired(100, false);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getSucceeded()).isZero();
        assertThat(result.getFailed()).isZero();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("CN-11：dryRun 只统计不落库")
    void closeExpiredDryRun_noWrite() {
        when(orderMapper.selectList(any())).thenReturn(List.of(expired(21L), expired(22L)));

        CloseExpiredResultDTO result = orderService.adminCloseExpired(100, true);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getSucceeded()).isEqualTo(2);
        verify(orderMapper, never()).update(any(), any());
        verify(marketingClient, never()).rollbackCoupon(any());
    }

    @Test
    @DisplayName("CN-11：逐单 CAS 成功/并发失败分别计入 succeeded / failed")
    void closeExpiredMixedResults() {
        when(orderMapper.selectList(any())).thenReturn(List.of(expired(31L), expired(32L)));
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1, 0);

        CloseExpiredResultDTO result = orderService.adminCloseExpired(100, false);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getSucceeded()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getItems()).anySatisfy(i -> {
            assertThat(i.getId()).isEqualTo(32L);
            assertThat(i.isOk()).isFalse();
            assertThat(i.getReason()).contains("状态已变更");
        });
        // 关单写 close_type=2（超时）
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper, org.mockito.Mockito.times(2)).update(isNull(), captor.capture());
        assertThat(((LambdaUpdateWrapper<?>) captor.getAllValues().get(0)).getParamNameValuePairs().values())
                .contains(103, 2);
    }

    @Test
    @DisplayName("CN-11：limit 默认 100，超出部分不处理")
    void closeExpiredDefaultLimit() {
        when(orderMapper.selectList(any())).thenReturn(List.of(expired(41L)));

        CloseExpiredResultDTO result = orderService.adminCloseExpired(null, true);

        assertThat(result.getTotal()).isEqualTo(1);
    }

    /* ==================== CN-12 修改物流 ==================== */

    @Test
    @DisplayName("CN-12：非已发货订单 → 409")
    void updateShipWrongStatus_conflict() {
        when(orderMapper.selectOne(any())).thenReturn(order(51L, 102));

        assertThatThrownBy(() -> orderService.adminUpdateShip(updateShipDTO(51L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("只有已发货订单可以修改物流单号");
    }

    @Test
    @DisplayName("CN-12：已改过一次（日志计数=1）→ 409 不能再次修改")
    void updateShipAlreadyUpdated_conflict() {
        when(orderMapper.selectOne(any())).thenReturn(order(52L, 201));
        when(orderLogMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> orderService.adminUpdateShip(updateShipDTO(52L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("物流单号已修改过，不能再次修改");
    }

    @Test
    @DisplayName("CN-12：首次修改成功，并写「旧 → 新」日志")
    void updateShipFirstTime_success() {
        Order order = order(53L, 201);
        order.setShipChannel("SF");
        order.setShipSn("SF0001");
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderLogMapper.selectCount(any())).thenReturn(0L);
        stubCas(1);

        orderService.adminUpdateShip(updateShipDTO(53L));

        ArgumentCaptor<OrderLog> captor = ArgumentCaptor.forClass(OrderLog.class);
        verify(orderLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("order:ship-update");
        assertThat(captor.getValue().getResult()).contains("SF0001").contains("→");
    }

    /* ==================== 辅助 ==================== */

    private void stubCas(int affected) {
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(affected);
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<Order> captureCasWrapper() {
        ArgumentCaptor<LambdaUpdateWrapper<Order>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(orderMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    private Order order(Long id, int status) {
        return new Order()
                .setId(id)
                .setOrderSn("SN" + id)
                .setUserId(1L)
                .setOrderStatus(status)
                .setDeleted(false);
    }

    private Order expired(Long id) {
        return order(id, 101).setExpireTime(LocalDateTime.now().minusMinutes(1));
    }

    private OrderShipDTO shipDTO(Long orderId) {
        OrderShipDTO dto = new OrderShipDTO();
        dto.setOrderId(orderId);
        dto.setShipChannel("SF");
        dto.setShipSn("SF123456");
        return dto;
    }

    private OrderShipDTO updateShipDTO(Long orderId) {
        OrderShipDTO dto = new OrderShipDTO();
        dto.setOrderId(orderId);
        dto.setShipChannel("YTO");
        dto.setShipSn("YT9999");
        return dto;
    }
}
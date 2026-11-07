package com.litemall.service.impl;

import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.domain.PO.Order;
import com.litemall.domain.dto.LockedStockDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderStatsMapper;
import com.litemall.mq.OrderTimeoutPublisher;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CN-IN-04 锁定库存查询（/inner/order/locked-stock）。
 * <p>口径：{@code order_status=101} 且 {@code og.deleted=0} 的 number 之和，不带时间条件；
 * 参数格式非法一律 400 PARAM_INVALID；结果按 productId 升序，无占用返回空数组。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderLockedStockTest {

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
    @Mock
    private OrderTimeoutPublisher orderTimeoutPublisher;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(cartMapper, orderGoodsMapper, aftersaleMapper, orderLogMapper, orderStatsMapper,
                goodsClient, userClient, marketingClient, stringRedisTemplate, orderTimeoutPublisher);
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "entityClass", Order.class);
    }

    @Test
    @DisplayName("CN-IN-04：不传 productIds → 聚合全部有占用的货品，productIds 传 null 给 mapper")
    void lockedStockAll() {
        when(orderMapper.selectLockedStock(isNull())).thenReturn(List.of(
                item(1001L, 3), item(1002L, 1)));

        LockedStockDTO dto = orderService.lockedStock(null);

        assertThat(dto.getList()).hasSize(2);
        assertThat(dto.getList()).extracting(LockedStockDTO.Item::getProductId)
                .containsExactly(1001L, 1002L);
        assertThat(dto.getList()).extracting(LockedStockDTO.Item::getLockedQty)
                .containsExactly(3, 1);
        verify(orderMapper).selectLockedStock(isNull());
    }

    @Test
    @DisplayName("CN-IN-04：空串等同不传")
    void lockedStockBlank() {
        when(orderMapper.selectLockedStock(isNull())).thenReturn(List.of());

        LockedStockDTO dto = orderService.lockedStock("   ");

        assertThat(dto.getList()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("CN-IN-04：逗号多值 → 解析为 Long 列表传给 mapper")
    void lockedStockMulti() {
        when(orderMapper.selectLockedStock(any())).thenReturn(List.of(item(1002L, 5)));

        LockedStockDTO dto = orderService.lockedStock("1001,1002");

        assertThat(dto.getList()).hasSize(1);
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderMapper).selectLockedStock(captor.capture());
        assertThat(captor.getValue()).containsExactly(1001L, 1002L);
    }

    @Test
    @DisplayName("CN-IN-04：mapper 返回 null → 出参 list 恒为空数组而非 null")
    void lockedStockNullRows() {
        when(orderMapper.selectLockedStock(isNull())).thenReturn(null);

        LockedStockDTO dto = orderService.lockedStock(null);

        assertThat(dto.getList()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("CN-IN-04：空段/非数字/重复 → 400 PARAM_INVALID，且不查库")
    void lockedStockInvalidFormat() {
        for (String bad : List.of("1001,,1002", ",1001", "1001,abc", "1001,1001")) {
            assertThatThrownBy(() -> orderService.lockedStock(bad))
                    .as("productIds=%s", bad)
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("productIds");
        }
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("CN-IN-04：超过 200 个 productId → 400")
    void lockedStockTooMany() {
        String tooMany = LongStream.rangeClosed(1, 201).mapToObj(String::valueOf)
                .collect(Collectors.joining(","));

        assertThatThrownBy(() -> orderService.lockedStock(tooMany))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能超过 200");
    }

    @Test
    @DisplayName("CN-IN-04：恰好 200 个 productId 合法")
    void lockedStockExactly200() {
        when(orderMapper.selectLockedStock(any())).thenReturn(new ArrayList<>());
        String ids = LongStream.rangeClosed(1, 200).mapToObj(String::valueOf)
                .collect(Collectors.joining(","));

        LockedStockDTO dto = orderService.lockedStock(ids);

        assertThat(dto.getList()).isEmpty();
    }

    private LockedStockDTO.Item item(Long productId, Integer lockedQty) {
        LockedStockDTO.Item item = new LockedStockDTO.Item();
        item.setProductId(productId);
        item.setLockedQty(lockedQty);
        return item;
    }
}
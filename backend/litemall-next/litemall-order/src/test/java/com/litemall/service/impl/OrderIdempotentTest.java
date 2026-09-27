package com.litemall.service.impl;

import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.PO.Cart;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1 验收项「下单幂等」（M4）单元测试。
 * <p>
 * 覆盖两个关键语义：
 * <ol>
 *   <li>token 已被消费（USED）时拒绝重复提交；</li>
 *   <li>下单过程失败时把 token 原子回退为 ROLLED_BACK（可重试），且异常必须继续抛出
 *       —— 否则 {@code @GlobalTransactional} 不会回滚。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class OrderIdempotentTest {

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
        UserContext.setUser(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("M4/验收：token 已处于 USED 态时再次提交被拒绝")
    void alreadyUsedToken_rejected() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("USED");

        OrderSubmitDTO dto = submitDTO("token-used");

        assertThatThrownBy(() -> orderService.submitOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("请勿重复提交订单");

        // 未进入业务分支：不应发生任何库存扣减
        verify(goodsClient, never()).deductStockBatch(any());
    }

    @Test
    @DisplayName("M4/验收：token 不存在/已过期时被拒绝")
    void missingToken_rejected() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("MISSING");

        OrderSubmitDTO dto = submitDTO("token-missing");

        assertThatThrownBy(() -> orderService.submitOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("请勿重复提交订单");

        verify(goodsClient, never()).deductStockBatch(any());
    }

    @Test
    @DisplayName("M4/验收：下单失败时 token 回退为 ROLLED_BACK，且异常继续抛出（保证全局事务回滚）")
    void submitFailure_rollsBackTokenAndRethrows() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("OK");
        when(cartMapper.selectByIds(anyCollection())).thenReturn(List.of(validCart()));
        // 收货地址不存在：在第 3 步失败，属于"业务校验失败"，应回退 token 并抛异常
        when(userClient.getAddressById(anyLong())).thenReturn(null);

        OrderSubmitDTO dto = submitDTO("token-fail");

        assertThatThrownBy(() -> orderService.submitOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("收货地址不存在");

        ArgumentCaptor<RedisScript<?>> captor = ArgumentCaptor.forClass(RedisScript.class);
        verify(stringRedisTemplate, times(2)).execute(captor.capture(), anyList());
        List<RedisScript<?>> scripts = captor.getAllValues();
        assertThat(scripts).hasSize(2);
        assertThat(scripts.get(0).getScriptAsString()).contains("'INIT'");
        assertThat(scripts.get(1).getScriptAsString()).contains("'ROLLED_BACK'");
    }

    private Cart validCart() {
        return new Cart()
                .setId(1L)
                .setUserId(1L)
                .setProductId(10L)
                .setGoodsId(100L)
                .setNumber(1)
                .setDeleted(false);
    }

    private OrderSubmitDTO submitDTO(String token) {
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setIdemToken(token);
        dto.setCartIds(List.of(1L));
        dto.setAddressId(1L);
        return dto;
    }
}
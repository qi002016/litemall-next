package com.litemall.service.impl;

import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.PO.Cart;
import com.litemall.domain.VO.OrderSubmitVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.exception.NotFoundException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderStatsMapper;
import com.litemall.mq.OrderTimeoutPublisher;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-82 下单幂等（P-02 / 07 §2.2）。
 * <p>三类语义：token 缺失/过期 → 400；同键同参 → 回放首次结果；同键不同参 → 409。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderIdempotentTest {

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
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private OrderMapper orderMapper;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(cartMapper, orderGoodsMapper, aftersaleMapper, orderLogMapper, orderStatsMapper,
                goodsClient, userClient, marketingClient, stringRedisTemplate, orderTimeoutPublisher);
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "entityClass", com.litemall.domain.PO.Order.class);
        UserContext.setUser(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("CI-82：token 不存在/已过期 → 400 PARAM_INVALID，不产生任何业务读写")
    void missingToken_rejected() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("MISSING");

        assertThatThrownBy(() -> orderService.submitOrder(submitDTO("token-missing")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("幂等token无效或已过期");

        verify(goodsClient, never()).deductStockBatch(any());
        verify(cartMapper, never()).selectByIds(anyCollection());
    }

    @Test
    @DisplayName("CI-82：同 idemToken 且参数不同 → 409 IDEMPOTENT_CONFLICT")
    void sameTokenDifferentParams_conflict() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("USED");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> orderService.submitOrder(submitDTO("token-used")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("幂等token已用于不同参数");

        verify(cartMapper, never()).selectByIds(anyCollection());
    }

    @Test
    @DisplayName("CI-82：同 idemToken 且参数相同 → 回放首次下单结果（不重复建单）")
    void sameTokenSameParams_replay() {
        OrderSubmitDTO dto = submitDTO("token-replay");
        String fingerprint = ReflectionTestUtils.invokeMethod(orderService, "fingerprint", dto);
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("USED");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(fingerprint
                + "|{\"orderId\":\"88\",\"orderSn\":\"SN88\",\"orderAmount\":1234,"
                + "\"expireTime\":\"2026-11-01 12:00:00\"}");

        OrderSubmitVO vo = orderService.submitOrder(dto);

        assertThat(vo.getOrderId()).isEqualTo(88L);
        assertThat(vo.getOrderSn()).isEqualTo("SN88");
        assertThat(vo.getOrderAmount()).isEqualTo(1234);
        verify(cartMapper, never()).selectByIds(anyCollection());
    }

    @Test
    @DisplayName("CI-82：业务失败时 token 原子回退 ROLLED_BACK，且异常继续抛出（保证全局事务回滚）")
    void submitFailure_rollsBackTokenAndRethrows() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("OK");
        when(cartMapper.selectByIds(anyCollection())).thenReturn(List.of(validCart()));
        when(goodsClient.getProductInfo(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> orderService.submitOrder(submitDTO("token-fail")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("商品规格不存在或已下架");

        ArgumentCaptor<RedisScript<?>> captor = ArgumentCaptor.forClass(RedisScript.class);
        verify(stringRedisTemplate, times(2)).execute(captor.capture(), anyList());
        List<RedisScript<?>> scripts = captor.getAllValues();
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
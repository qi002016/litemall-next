package com.litemall.service.impl;

import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.domain.PO.Cart;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderGoods;
import com.litemall.domain.dto.AddressInfoDTO;
import com.litemall.domain.dto.CouponUseDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.domain.DTO.OrderConfirmDTO;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.VO.OrderConfirmLineVO;
import com.litemall.domain.VO.OrderConfirmVO;
import com.litemall.domain.VO.OrderSubmitVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderStatsMapper;
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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-80 试算 / CI-82 提交：金额拆分（DR-08 D9 分摊）与库存锁定口径。
 * <p>契约要点：金额对外一律「分」；券额按行商品金额占比分摊、尾差入最后一行；
 * 可售不足在 CI-80 只提示（availableStock），在 CI-82 整单 409 STOCK_NOT_ENOUGH；
 * 下单只落订单行，<b>不调 deductStockBatch</b>（实扣唯一时点是 CI-97）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderConfirmAllocationTest {

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
        UserContext.setUser(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /* ==================== CI-80 试算 ==================== */

    @Test
    @DisplayName("CI-80：立即购买单行，金额转分且券额全落该行")
    void confirmBuyNowSingleLine() {
        when(goodsClient.getProductInfo(1001L)).thenReturn(product(1001L, "100.00", 5));
        when(userClient.getAddressById(1L)).thenReturn(address());
        when(marketingClient.checkCoupon(any())).thenReturn(new BigDecimal("30.00"));

        OrderConfirmDTO dto = confirmBuyNow(1001L, 3, 1L);
        dto.setCouponId(7L);
        OrderConfirmVO vo = orderService.confirmOrder(dto);

        assertThat(vo.getGoodsAmount()).isEqualTo(30000);
        assertThat(vo.getFreightAmount()).isZero();
        assertThat(vo.getCouponAmount()).isEqualTo(3000);
        assertThat(vo.getOrderAmount()).isEqualTo(27000);
        assertThat(vo.getLines()).hasSize(1);
        OrderConfirmLineVO line = vo.getLines().get(0);
        assertThat(line.getProductId()).isEqualTo(1001L);
        assertThat(line.getPrice()).isEqualTo(10000);
        assertThat(line.getNumber()).isEqualTo(3);
        assertThat(line.getLineAmount()).isEqualTo(30000);
        assertThat(line.getCouponShare()).isEqualTo(3000);
        assertThat(line.getActualAmount()).isEqualTo(27000);
        assertThat(line.getAvailableStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("CI-80：两行按商品金额占比分摊券额（10 元券 / 100+50 → 6.67 + 3.33）")
    void confirmCartTwoLinesAllocation() {
        when(cartMapper.selectByIds(anyList())).thenReturn(List.of(
                cart(11L, 1L, 1001L, "100.00", 1),
                cart(12L, 1L, 1002L, "50.00", 1)));
        when(goodsClient.getProductInfo(1001L)).thenReturn(product(1001L, "100.00", 10));
        when(goodsClient.getProductInfo(1002L)).thenReturn(product(1002L, "50.00", 10));
        when(userClient.getAddressById(1L)).thenReturn(address());
        when(marketingClient.checkCoupon(any())).thenReturn(new BigDecimal("10.00"));

        OrderConfirmDTO dto = new OrderConfirmDTO();
        dto.setCartIds(List.of(11L, 12L));
        dto.setAddressId(1L);
        dto.setCouponId(7L);

        OrderConfirmVO vo = orderService.confirmOrder(dto);

        assertThat(vo.getGoodsAmount()).isEqualTo(15000);
        assertThat(vo.getCouponAmount()).isEqualTo(1000);
        assertThat(vo.getOrderAmount()).isEqualTo(14000);
        assertThat(vo.getLines()).extracting(OrderConfirmLineVO::getCouponShare)
                .containsExactly(667, 333);
        assertThat(vo.getLines()).extracting(OrderConfirmLineVO::getActualAmount)
                .containsExactly(9333, 4667);
        assertThat(vo.getLines().stream().mapToInt(OrderConfirmLineVO::getCouponShare).sum()).isEqualTo(1000);
    }

    @Test
    @DisplayName("CI-80：可售不足只提示不阻断（availableStock 回填，不抛 409）")
    void confirmShortStockOnlyHints() {
        when(goodsClient.getProductInfo(1001L)).thenReturn(product(1001L, "9.90", 1));
        when(userClient.getAddressById(1L)).thenReturn(address());

        OrderConfirmVO vo = orderService.confirmOrder(confirmBuyNow(1001L, 5, 1L));

        assertThat(vo.getLines()).hasSize(1);
        assertThat(vo.getLines().get(0).getAvailableStock()).isEqualTo(1);
        assertThat(vo.getOrderAmount()).isEqualTo(4950);
    }

    @Test
    @DisplayName("CI-80：cartIds 与 buyNow 都不传 → 400 PARAM_INVALID")
    void confirmWithoutSource() {
        OrderConfirmDTO dto = new OrderConfirmDTO();
        dto.setAddressId(1L);

        assertThatThrownBy(() -> orderService.confirmOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("二选一");
    }

    @Test
    @DisplayName("CI-80：cartIds 与 buyNow 同时传 → 400 PARAM_INVALID")
    void confirmWithBothSources() {
        OrderConfirmDTO dto = confirmBuyNow(1001L, 1, 1L);
        dto.setCartIds(List.of(11L));

        assertThatThrownBy(() -> orderService.confirmOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("二选一");
    }

    @Test
    @DisplayName("CI-80：地址已删（user 服务返回 null）→ 400 ADDRESS_INVALID")
    void confirmAddressMissing() {
        when(goodsClient.getProductInfo(1001L)).thenReturn(product(1001L, "9.90", 9));
        when(userClient.getAddressById(1L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.confirmOrder(confirmBuyNow(1001L, 1, 1L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("收货地址不存在");
    }

    /* ==================== CI-82 提交 ==================== */

    @Test
    @DisplayName("CI-82：可售不足 → 409 STOCK_NOT_ENOUGH，msg 含规格与「仅剩 N 件」，不建单")
    void submitStockNotEnough() {
        when(goodsClient.getProductInfo(1001L)).thenReturn(product(1001L, "10.00", 2));
        when(userClient.getAddressById(1L)).thenReturn(address());
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("OK");

        assertThatThrownBy(() -> orderService.submitOrder(submitBuyNow(1001L, 3, 1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("仅剩 2 件");
        verify(orderMapper, never()).insert(any(Order.class));
        verify(goodsClient, never()).deductStockBatch(any());
    }

    @Test
    @DisplayName("CI-82：成功建单落行快照（skuCode/券分摊/退款态），expireTime=下单+30min，且不扣库存")
    void submitSuccessSnapshot() {
        when(goodsClient.getProductInfo(1001L)).thenReturn(product(1001L, "100.00", 10));
        when(userClient.getAddressById(1L)).thenReturn(address());
        when(marketingClient.checkCoupon(any())).thenReturn(new BigDecimal("30.00"));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("OK");
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(9001L);
            return 1;
        });

        LocalDateTime before = LocalDateTime.now();
        OrderSubmitDTO submit = submitBuyNow(1001L, 3, 1L);
        submit.setCouponId(7L);
        OrderSubmitVO vo = orderService.submitOrder(submit);

        assertThat(vo.getOrderId()).isEqualTo(9001L);
        assertThat(vo.getOrderAmount()).isEqualTo(27000);
        assertThat(vo.getExpireTime()).isNotBlank();
        LocalDateTime expire = LocalDateTime.parse(vo.getExpireTime().replace(' ', 'T'));
        assertThat(expire).isAfter(before.plusMinutes(29)).isBefore(before.plusMinutes(31));

        ArgumentCaptor<OrderGoods> lineCaptor = ArgumentCaptor.forClass(OrderGoods.class);
        verify(orderGoodsMapper).insert(lineCaptor.capture());
        OrderGoods line = lineCaptor.getValue();
        assertThat(line.getOrderId()).isEqualTo(9001L);
        assertThat(line.getProductId()).isEqualTo(1001L);
        assertThat(line.getNumber()).isEqualTo(3);
        assertThat(line.getSkuCode()).isEqualTo("215-3_7");
        assertThat(line.getCouponShare()).isEqualByComparingTo("30.00");
        assertThat(line.getActualAmount()).isEqualByComparingTo("270.00");
        assertThat(line.getRefundStatus()).isZero();
        assertThat(line.getRefundedNumber()).isZero();

        // 券核销一次；下单不实扣库存
        ArgumentCaptor<CouponUseDTO> couponCaptor = ArgumentCaptor.forClass(CouponUseDTO.class);
        verify(marketingClient).useCoupon(couponCaptor.capture());
        assertThat(couponCaptor.getValue().getOrderId()).isEqualTo(9001L);
        verify(goodsClient, never()).deductStockBatch(any());
        verify(goodsClient, never()).releaseStockBatch(any());
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("CI-82：购物车下单成功后逻辑删除已购项")
    void submitClearsCart() {
        when(cartMapper.selectByIds(anyList())).thenReturn(List.of(cart(11L, 1L, 1001L, "10.00", 2)));
        when(goodsClient.getProductInfo(1001L)).thenReturn(product(1001L, "10.00", 10));
        when(userClient.getAddressById(1L)).thenReturn(address());
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("OK");
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(9002L);
            return 1;
        });

        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setCartIds(List.of(11L));
        dto.setAddressId(1L);
        dto.setIdemToken("tok-cart");

        orderService.submitOrder(dto);

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(11L);
        assertThat(captor.getValue().getDeleted()).isTrue();
        verify(marketingClient, never()).useCoupon(any());
    }

    @Test
    @DisplayName("CI-82：超过 50 行 → 400 ORDER_ITEM_LIMIT")
    void submitItemLimit() {
        List<Long> cartIds = IntStream.rangeClosed(1, 51).mapToObj(Long::valueOf).collect(Collectors.toList());
        List<Cart> carts = new ArrayList<>();
        for (Long id : cartIds) {
            carts.add(cart(id, 1L, 2000L + id, "1.00", 1));
        }
        when(cartMapper.selectByIds(anyList())).thenReturn(carts);
        when(goodsClient.getProductInfo(any())).thenReturn(product(2001L, "1.00", 100));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList())).thenReturn("OK");

        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setCartIds(cartIds);
        dto.setAddressId(1L);
        dto.setIdemToken("tok-limit");

        assertThatThrownBy(() -> orderService.submitOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能超过 50");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("CI-82：缺 idemToken → 400 PARAM_INVALID，且不触碰 Redis")
    void submitWithoutIdemToken() {
        OrderSubmitDTO dto = submitBuyNow(1001L, 1, 1L);
        dto.setIdemToken("  ");

        assertThatThrownBy(() -> orderService.submitOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("幂等token不能为空");
        verify(stringRedisTemplate, never()).execute(any(RedisScript.class), anyList());
    }

    /* ==================== 辅助 ==================== */

    private OrderConfirmDTO confirmBuyNow(Long productId, int number, Long addressId) {
        OrderConfirmDTO.BuyNow buyNow = new OrderConfirmDTO.BuyNow();
        buyNow.setProductId(productId);
        buyNow.setNumber(number);
        OrderConfirmDTO dto = new OrderConfirmDTO();
        dto.setBuyNow(buyNow);
        dto.setAddressId(addressId);
        return dto;
    }

    private OrderSubmitDTO submitBuyNow(Long productId, int number, Long addressId) {
        OrderConfirmDTO.BuyNow buyNow = new OrderConfirmDTO.BuyNow();
        buyNow.setProductId(productId);
        buyNow.setNumber(number);
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setBuyNow(buyNow);
        dto.setAddressId(addressId);
        dto.setIdemToken("tok-buy-now");
        return dto;
    }

    private GoodsProductInfoDTO product(Long productId, String price, Integer sellable) {
        GoodsProductInfoDTO dto = new GoodsProductInfoDTO();
        dto.setId(productId);
        dto.setGoodsId(productId + 100);
        dto.setGoodsName("山地自行车 " + productId);
        dto.setGoodsSn("SN-" + productId);
        dto.setPrice(new BigDecimal(price));
        dto.setSpecifications("[\"黑色\",\"L\"]");
        dto.setSkuCode("215-3_7");
        dto.setSpecValueIds(List.of("3", "7"));
        dto.setSellable(sellable);
        dto.setNumber(sellable);
        return dto;
    }

    private Cart cart(Long id, Long userId, Long productId, String price, int number) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setUserId(userId);
        cart.setGoodsId(productId + 100);
        cart.setGoodsName("山地自行车 " + productId);
        cart.setProductId(productId);
        cart.setPrice(new BigDecimal(price));
        cart.setNumber(number);
        cart.setSpecifications("[\"黑色\",\"L\"]");
        cart.setDeleted(false);
        return cart;
    }

    private AddressInfoDTO address() {
        AddressInfoDTO dto = new AddressInfoDTO();
        dto.setId(1L);
        dto.setName("张三");
        dto.setMobile("13800138000");
        dto.setProvince("上海市");
        dto.setCity("上海市");
        dto.setDistrict("浦东新区");
        dto.setAddress("世纪大道 1 号");
        return dto;
    }
}
package com.litemall.service.impl;

import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.domain.DTO.CartAddDTO;
import com.litemall.domain.DTO.CartUpdateDTO;
import com.litemall.domain.DTO.OrderConfirmDTO;
import com.litemall.domain.PO.Cart;
import com.litemall.domain.VO.OrderConfirmVO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CartMapper;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M14：{@code CartServiceImpl} 的异常语义与校验补强。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>裸 {@code RuntimeException} → {@code BadRequestException}（对外是 400 而非 500）；</li>
 *   <li>加购数量下界与上限（累加后判定，避免少量多次绕过）；</li>
 *   <li>改数量时的范围校验；</li>
 *   <li>结算时的购物车归属完整性校验（不再"静默丢弃别人的行"）；</li>
 *   <li>金额钳制（券额超过商品金额、订单金额不得为负）。</li>
 * </ul>
 * 说明：{@code CartServiceImpl} 继承 MyBatis-Plus 的 {@code ServiceImpl}，
 * 其 {@code baseMapper}/{@code entityClass} 由 Spring 注入，这里用 {@link ReflectionTestUtils} 手工补上。
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final int MAX_ITEM_NUMBER = 999;

    @Mock
    private CartMapper cartMapper;
    @Mock
    private GoodsClient goodsClient;
    @Mock
    private MarketingClient marketingClient;

    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(goodsClient, marketingClient);
        ReflectionTestUtils.setField(cartService, "baseMapper", cartMapper);
        ReflectionTestUtils.setField(cartService, "entityClass", Cart.class);
        UserContext.setUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /* ==================== 加购数量 ==================== */

    @Test
    @DisplayName("M14：加购数量为 0 被拒，且不调用商品服务")
    void addCart_zeroNumber_rejected() {
        CartAddDTO dto = addDTO(1);
        dto.setNumber(0);

        assertThatThrownBy(() -> cartService.addCart(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("加购数量必须大于 0");

        verify(goodsClient, never()).getDefaultProduct(any());
        verify(goodsClient, never()).getProductInfo(any());
    }

    @Test
    @DisplayName("M14：加购数量为负被拒")
    void addCart_negativeNumber_rejected() {
        CartAddDTO dto = addDTO(-3);

        assertThatThrownBy(() -> cartService.addCart(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("加购数量必须大于 0");
    }

    @Test
    @DisplayName("M14：加购数量为 null 按 0 处理并被拒")
    void addCart_nullNumber_rejected() {
        CartAddDTO dto = addDTO(null);

        assertThatThrownBy(() -> cartService.addCart(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("加购数量必须大于 0");
    }

    /* ==================== 修改数量 ==================== */

    @Test
    @DisplayName("M14：商品ID为空时改数量被拒")
    void updateCart_nullGoodsId_rejected() {
        CartUpdateDTO dto = new CartUpdateDTO();
        dto.setNumber(1);

        assertThatThrownBy(() -> cartService.updateCart(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("商品ID不能为空");
    }

    @Test
    @DisplayName("M14：购物车项不存在（或不属于当前用户）时抛 BadRequestException 而非 RuntimeException")
    void updateCart_cartMissing_badRequest() {
        when(cartMapper.selectOne(any())).thenReturn(null);
        CartUpdateDTO dto = updateDTO(5);

        assertThatThrownBy(() -> cartService.updateCart(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("购物车项不存在或不属于当前用户");
    }

    @Test
    @DisplayName("M14：改数量超过上限被拒，且不写库")
    void updateCart_numberOverLimit_rejected() {
        when(cartMapper.selectOne(any())).thenReturn(cart(1L, USER_ID, 10, 2));
        CartUpdateDTO dto = updateDTO(MAX_ITEM_NUMBER + 1);

        assertThatThrownBy(() -> cartService.updateCart(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("购物车数量必须在 1 ~ " + MAX_ITEM_NUMBER + " 之间");

        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    @DisplayName("M14：改数量为 0 被拒，且不写库")
    void updateCart_zeroNumber_rejected() {
        when(cartMapper.selectOne(any())).thenReturn(cart(1L, USER_ID, 10, 2));
        CartUpdateDTO dto = updateDTO(0);

        assertThatThrownBy(() -> cartService.updateCart(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("购物车数量必须在 1 ~ " + MAX_ITEM_NUMBER + " 之间");

        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    /* ==================== 结算：归属完整性 ==================== */

    @Test
    @DisplayName("M14：结算未选择任何购物车项被拒")
    void confirmOrder_emptyCartIds_rejected() {
        OrderConfirmDTO dto = new OrderConfirmDTO();
        dto.setCartIds(List.of());

        assertThatThrownBy(() -> cartService.confirmOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("请选择要结算的购物车项");
    }

    @Test
    @DisplayName("M14：结算 cartIds 为 null 被拒")
    void confirmOrder_nullCartIds_rejected() {
        OrderConfirmDTO dto = new OrderConfirmDTO();
        dto.setCartIds(null);

        assertThatThrownBy(() -> cartService.confirmOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("请选择要结算的购物车项");
    }

    @Test
    @DisplayName("M14：结算包含他人购物车项时明确拒绝（而非静默忽略该行）")
    void confirmOrder_foreignCartItem_rejected() {
        when(cartMapper.selectByIds(anyCollection())).thenReturn(List.of(cart(1L, 999L, 10, 1)));
        OrderConfirmDTO dto = confirmDTO(1L);

        assertThatThrownBy(() -> cartService.confirmOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("购物车项不存在或不属于当前用户");

        verify(marketingClient, never()).checkCoupon(any());
    }

    @Test
    @DisplayName("M14：结算包含已删除购物车项时被拒")
    void confirmOrder_deletedCartItem_rejected() {
        Cart deleted = cart(1L, USER_ID, 10, 1);
        deleted.setDeleted(true);
        when(cartMapper.selectByIds(anyCollection())).thenReturn(List.of(deleted));

        assertThatThrownBy(() -> cartService.confirmOrder(confirmDTO(1L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("购物车项不存在或不属于当前用户");
    }

    @Test
    @DisplayName("M14：请求的 cartId 查不到（数量不符）时被拒")
    void confirmOrder_missingCartItem_rejected() {
        when(cartMapper.selectByIds(anyCollection())).thenReturn(List.of(cart(1L, USER_ID, 10, 1)));

        OrderConfirmDTO dto = confirmDTO(1L, 2L);

        assertThatThrownBy(() -> cartService.confirmOrder(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("购物车项不存在或不属于当前用户");
    }

    /* ==================== 结算：金额钳制 ==================== */

    @Test
    @DisplayName("M14：券额超过商品金额时被钳制，订单金额不为负")
    void confirmOrder_couponExceedsGoodsAmount_clamped() {
        when(cartMapper.selectByIds(anyCollection())).thenReturn(List.of(cart(1L, USER_ID, 10, 3)));
        when(marketingClient.checkCoupon(any())).thenReturn(new BigDecimal("100"));

        OrderConfirmVO vo = cartService.confirmOrder(confirmDTO(1L));

        assertThat(vo.getGoodsAmount()).isEqualByComparingTo("30");
        assertThat(vo.getCouponAmount()).isEqualByComparingTo("30");
        assertThat(vo.getOrderAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("M14：券额返回 null 时按 0 处理，订单金额等于商品金额")
    void confirmOrder_nullCoupon_treatedAsZero() {
        when(cartMapper.selectByIds(anyCollection())).thenReturn(List.of(cart(1L, USER_ID, 10, 3)));
        when(marketingClient.checkCoupon(any())).thenReturn(null);

        OrderConfirmVO vo = cartService.confirmOrder(confirmDTO(1L));

        assertThat(vo.getCouponAmount()).isEqualByComparingTo("0");
        assertThat(vo.getOrderAmount()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("M14：券额为负时按 0 处理")
    void confirmOrder_negativeCoupon_treatedAsZero() {
        when(cartMapper.selectByIds(anyCollection())).thenReturn(List.of(cart(1L, USER_ID, 10, 3)));
        when(marketingClient.checkCoupon(any())).thenReturn(new BigDecimal("-5"));

        OrderConfirmVO vo = cartService.confirmOrder(confirmDTO(1L));

        assertThat(vo.getCouponAmount()).isEqualByComparingTo("0");
        assertThat(vo.getOrderAmount()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("M14：正常结算金额正确（多行累加并扣券）")
    void confirmOrder_normalAmounts() {
        when(cartMapper.selectByIds(anyCollection()))
                .thenReturn(List.of(cart(1L, USER_ID, 10, 3), cart(2L, USER_ID, 5, 2)));
        when(marketingClient.checkCoupon(any())).thenReturn(new BigDecimal("5"));

        OrderConfirmVO vo = cartService.confirmOrder(confirmDTO(1L, 2L));

        assertThat(vo.getGoodsAmount()).isEqualByComparingTo("40");
        assertThat(vo.getCouponAmount()).isEqualByComparingTo("5");
        assertThat(vo.getOrderAmount()).isEqualByComparingTo("35");
        assertThat(vo.getFreightAmount()).isEqualByComparingTo("0");
    }

    /* ==================== 夹具 ==================== */

    private CartAddDTO addDTO(Integer number) {
        CartAddDTO dto = new CartAddDTO();
        dto.setGoodsId(100L);
        dto.setSpecId(0L);
        dto.setNumber(number);
        return dto;
    }

    /** specId 传非 0，避免 resolveProductId 触发 Feign 调用 */
    private CartUpdateDTO updateDTO(Integer number) {
        CartUpdateDTO dto = new CartUpdateDTO();
        dto.setGoodsId(100L);
        dto.setSpecId(200L);
        dto.setNumber(number);
        return dto;
    }

    private OrderConfirmDTO confirmDTO(Long... cartIds) {
        OrderConfirmDTO dto = new OrderConfirmDTO();
        dto.setCartIds(List.of(cartIds));
        dto.setAddressId(1L);
        dto.setCouponId(9L);
        return dto;
    }

    private Cart cart(Long id, Long userId, int price, int number) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setUserId(userId);
        cart.setGoodsId(100L);
        cart.setProductId(200L);
        cart.setPrice(BigDecimal.valueOf(price));
        cart.setNumber(number);
        cart.setChecked(true);
        cart.setDeleted(false);
        return cart;
    }
}
package com.litemall.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.domain.DTO.CartAddDTO;
import com.litemall.domain.DTO.CartUpdateDTO;
import com.litemall.domain.DTO.OrderConfirmDTO;
import com.litemall.domain.PO.Cart;
import com.litemall.domain.VO.CartItemVO;
import com.litemall.domain.VO.OrderConfirmVO;
import com.litemall.domain.dto.CouponCheckDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.mapper.CartMapper;
import com.litemall.service.ICartService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

    private final GoodsClient goodsClient;
    private final MarketingClient marketingClient;

    @Override
    public List<CartItemVO> listCart() {
        Long userId = UserContext.getUser();
        List<Cart> carts = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getDeleted, false)
                .orderByDesc(Cart::getAddTime)
                .list();
        return carts.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> addCart(CartAddDTO dto) {
        Long userId = UserContext.getUser();
        Long goodsId = dto.getGoodsId();

        // 通过 Feign 查询商品货品信息（价格/规格/图/名）
        // 前端未选规格时 specId 传 0，取该商品默认货品
        GoodsProductInfoDTO product = resolveProduct(dto.getGoodsId(), dto.getSpecId());
        if (product == null) {
            throw new RuntimeException("商品规格不存在");
        }
        Long productId = product.getId();

        Cart exist = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getGoodsId, goodsId)
                .eq(Cart::getProductId, productId)
                .eq(Cart::getDeleted, false)
                .one();

        int total;
        if (exist != null) {
            exist.setNumber(exist.getNumber() + dto.getNumber());
            exist.setUpdateTime(LocalDateTime.now());
            updateById(exist);
            total = exist.getNumber();
        } else {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setGoodsId(goodsId);
            cart.setProductId(productId);
            cart.setNumber(dto.getNumber());
            cart.setChecked(true);
            cart.setPrice(product.getPrice());
            cart.setSpecifications(product.getSpecifications());
            cart.setPicUrl(StrUtil.isNotBlank(product.getUrl()) ? product.getUrl() : product.getPicUrl());
            cart.setGoodsName(product.getGoodsName());
            cart.setGoodsSn(product.getGoodsSn());
            cart.setAddTime(LocalDateTime.now());
            cart.setUpdateTime(LocalDateTime.now());
            save(cart);
            total = dto.getNumber();
            exist = cart;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("cartId", exist.getId());
        result.put("goodsId", goodsId);
        result.put("number", total);
        return result;
    }

    @Override
    public CartItemVO updateCart(CartUpdateDTO dto) {
        Long userId = UserContext.getUser();
        Long productId = resolveProductId(dto.getGoodsId(), dto.getSpecId());
        Cart cart = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getGoodsId, dto.getGoodsId())
                .eq(Cart::getProductId, productId)
                .eq(Cart::getDeleted, false)
                .one();
        if (cart == null) {
            throw new RuntimeException("购物车项不存在");
        }
        if (dto.getNumber() != null) {
            cart.setNumber(dto.getNumber());
        }
        if (dto.getChecked() != null) {
            cart.setChecked(dto.getChecked());
        }
        cart.setUpdateTime(LocalDateTime.now());
        updateById(cart);
        return toVO(cart);
    }

    @Override
    public void deleteCart(Long goodsId, Long specId) {
        Long userId = UserContext.getUser();
        Long productId = resolveProductId(goodsId, specId);
        Cart cart = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getGoodsId, goodsId)
                .eq(Cart::getProductId, productId)
                .eq(Cart::getDeleted, false)
                .one();
        if (cart != null) {
            cart.setDeleted(true);
            cart.setUpdateTime(LocalDateTime.now());
            updateById(cart);
        }
    }

    @Override
    public OrderConfirmVO confirmOrder(OrderConfirmDTO dto) {
        Long userId = UserContext.getUser();
        List<Cart> carts = listByIds(dto.getCartIds());
        BigDecimal goodsAmount = carts.stream()
                .filter(c -> c.getUserId().equals(userId) && !Boolean.TRUE.equals(c.getDeleted()))
                .map(c -> c.getPrice().multiply(BigDecimal.valueOf(c.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal freight = BigDecimal.ZERO;
        // 优惠券抵扣试算（跨服务调用 marketing）
        CouponCheckDTO check = new CouponCheckDTO();
        check.setCouponUserId(dto.getCouponId());
        check.setGoodsAmount(goodsAmount);
        BigDecimal coupon = marketingClient.checkCoupon(check);
        OrderConfirmVO vo = new OrderConfirmVO();
        vo.setGoodsAmount(goodsAmount);
        vo.setFreightAmount(freight);
        vo.setCouponAmount(coupon);
        vo.setOrderAmount(goodsAmount.add(freight).subtract(coupon));
        return vo;
    }

    private CartItemVO toVO(Cart cart) {
        CartItemVO vo = new CartItemVO();
        vo.setId(cart.getId());
        vo.setGoodsId(cart.getGoodsId());
        vo.setGoodsName(cart.getGoodsName());
        vo.setGoodsPic(cart.getPicUrl());
        vo.setPrice(cart.getPrice());
        vo.setNumber(cart.getNumber());
        vo.setChecked(cart.getChecked());
        vo.setSpecId(cart.getProductId());
        vo.setSpec(parseSpec(cart.getSpecifications()));
        return vo;
    }

    /** specId 为空或 0（前端未选规格）时取该商品默认货品；否则按货品ID查询 */
    private GoodsProductInfoDTO resolveProduct(Long goodsId, Long specId) {
        if (specId == null || specId == 0) {
            return goodsClient.getDefaultProduct(goodsId);
        }
        return goodsClient.getProductInfo(specId);
    }

    /** 解析真实货品ID（容错 specId=0，取默认货品）；查不到返回 null */
    private Long resolveProductId(Long goodsId, Long specId) {
        if (specId != null && specId != 0) {
            return specId;
        }
        GoodsProductInfoDTO product = resolveProduct(goodsId, specId);
        return product == null ? null : product.getId();
    }

    /** specifications 形如 ["红色","XL"]，拼成 "红色 / XL" */
    private String parseSpec(String json) {
        if (StrUtil.isBlank(json)) {
            return "";
        }
        try {
            List<String> arr = JSONUtil.toList(json, String.class);
            return String.join(" / ", arr);
        } catch (Exception e) {
            return json;
        }
    }
}

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
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CartMapper;
import com.litemall.service.ICartService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

    private final GoodsClient goodsClient;
    private final MarketingClient marketingClient;

    /** M14：单行购物车数量上限。加购是累加语义，故上限要在"累加后"判定 */
    private static final int MAX_ITEM_NUMBER = 999;

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

        // M14：数量下界在服务层兜底（控制器校验可能被内部调用或后续新增入口绕过）
        int addNumber = dto.getNumber() == null ? 0 : dto.getNumber();
        if (addNumber < 1) {
            throw new BadRequestException("加购数量必须大于 0");
        }

        // 通过 Feign 查询商品货品信息（价格/规格/图/名）
        // 前端未选规格时 specId 传 0，取该商品默认货品
        GoodsProductInfoDTO product = resolveProduct(dto.getGoodsId(), dto.getSpecId());
        if (product == null) {
            throw new BadRequestException("商品规格不存在");
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
            int current = exist.getNumber() == null ? 0 : exist.getNumber();
            // M14：在"累加后"判定上限，避免少量多次绕过
            if (current + addNumber > MAX_ITEM_NUMBER) {
                throw new BadRequestException("该商品最多加购 " + MAX_ITEM_NUMBER + " 件，当前已有 " + current + " 件");
            }
            exist.setNumber(current + addNumber);
            exist.setUpdateTime(LocalDateTime.now());
            updateById(exist);
            total = exist.getNumber();
        } else {
            if (addNumber > MAX_ITEM_NUMBER) {
                throw new BadRequestException("该商品最多加购 " + MAX_ITEM_NUMBER + " 件");
            }
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setGoodsId(goodsId);
            cart.setProductId(productId);
            cart.setNumber(addNumber);
            cart.setChecked(true);
            cart.setPrice(product.getPrice());
            cart.setSpecifications(product.getSpecifications());
            cart.setPicUrl(StrUtil.isNotBlank(product.getUrl()) ? product.getUrl() : product.getPicUrl());
            cart.setGoodsName(product.getGoodsName());
            cart.setGoodsSn(product.getGoodsSn());
            cart.setAddTime(LocalDateTime.now());
            cart.setUpdateTime(LocalDateTime.now());
            save(cart);
            total = addNumber;
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
        if (dto.getGoodsId() == null) {
            throw new BadRequestException("商品ID不能为空");
        }
        Long productId = resolveProductId(dto.getGoodsId(), dto.getSpecId());
        Cart cart = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getGoodsId, dto.getGoodsId())
                .eq(Cart::getProductId, productId)
                .eq(Cart::getDeleted, false)
                .one();
        if (cart == null) {
            // 查询按 userId 过滤，故查不到既可能是"不存在"也可能是"不属于当前用户"
            throw new BadRequestException("购物车项不存在或不属于当前用户");
        }
        if (dto.getNumber() != null) {
            if (dto.getNumber() < 1 || dto.getNumber() > MAX_ITEM_NUMBER) {
                throw new BadRequestException("购物车数量必须在 1 ~ " + MAX_ITEM_NUMBER + " 之间");
            }
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
        List<Long> requestedIds = dto.getCartIds();
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new BadRequestException("请选择要结算的购物车项");
        }
        List<Cart> carts = listByIds(requestedIds);
        // M14 归属完整性校验：请求的每一项都必须存在、属于当前用户且未删除。
        // 原实现是"先把请求的 ID 全量取回，再在内存里过滤掉别人的项"——这会让用户
        // 看到与自己勾选不一致的金额（被过滤的行悄悄不参与计算），而不是明确报错。
        Set<Long> requested = new HashSet<>(requestedIds);
        if (carts.size() != requested.size()
                || carts.stream().anyMatch(c -> !userId.equals(c.getUserId()) || Boolean.TRUE.equals(c.getDeleted()))) {
            throw new BadRequestException("购物车项不存在或不属于当前用户");
        }

        BigDecimal goodsAmount = carts.stream()
                .map(c -> {
                    BigDecimal price = c.getPrice() == null ? BigDecimal.ZERO : c.getPrice();
                    int number = c.getNumber() == null ? 0 : c.getNumber();
                    return price.multiply(BigDecimal.valueOf(number));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal freight = BigDecimal.ZERO;
        // 优惠券抵扣试算（跨服务调用 marketing）
        CouponCheckDTO check = new CouponCheckDTO();
        check.setCouponUserId(dto.getCouponId());
        check.setGoodsAmount(goodsAmount);
        BigDecimal coupon = marketingClient.checkCoupon(check);
        // M14 金额钳制：券额可能为 null / 负 / 超过商品金额；订单金额不得为负
        if (coupon == null || coupon.signum() < 0) {
            coupon = BigDecimal.ZERO;
        }
        if (coupon.compareTo(goodsAmount) > 0) {
            coupon = goodsAmount;
        }
        BigDecimal orderAmount = goodsAmount.add(freight).subtract(coupon);
        if (orderAmount.signum() < 0) {
            orderAmount = BigDecimal.ZERO;
        }

        OrderConfirmVO vo = new OrderConfirmVO();
        vo.setGoodsAmount(goodsAmount);
        vo.setFreightAmount(freight);
        vo.setCouponAmount(coupon);
        vo.setOrderAmount(orderAmount);
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

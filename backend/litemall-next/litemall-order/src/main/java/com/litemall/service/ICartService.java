package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.CartAddDTO;
import com.litemall.domain.DTO.CartUpdateDTO;
import com.litemall.domain.DTO.OrderConfirmDTO;
import com.litemall.domain.PO.Cart;
import com.litemall.domain.VO.CartItemVO;
import com.litemall.domain.VO.OrderConfirmVO;

import java.util.List;
import java.util.Map;

public interface ICartService extends IService<Cart> {

    /** 当前用户购物车列表 */
    List<CartItemVO> listCart();

    /** 加入购物车，返回 cartId/goodsId/该规格总数量 */
    Map<String, Object> addCart(CartAddDTO dto);

    /** 修改购物车项 */
    CartItemVO updateCart(CartUpdateDTO dto);

    /** 删除购物车项 */
    void deleteCart(Long goodsId, Long specId);

    /** 订单确认页试算 */
    OrderConfirmVO confirmOrder(OrderConfirmDTO dto);
}

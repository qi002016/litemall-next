package com.litemall.client;

import com.litemall.domain.dto.OrderPayInfoDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * <p>
 * 订单服务 Feign 客户端（pay 服务跨服务调用）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@FeignClient(name = "litemall-order")
public interface OrderClient {

    /**
     * 查询订单支付信息（支付下单/结果查询）
     *
     * @param orderId 订单ID
     * @return 订单支付信息；订单不存在返回 null
     */
    @GetMapping("/inner/order/pay/info")
    OrderPayInfoDTO getPayInfo(@RequestParam("orderId") Long orderId);

    /**
     * 支付成功：订单 未付款 → 待发货，并写入支付时间与支付单号
     *
     * @param dto 订单ID + 微信支付单号
     */
    @PostMapping("/inner/order/pay/success")
    void markPaid(@RequestBody PaySuccessDTO dto);

    /**
     * 退款申请：订单置为售后状态，记录退款金额与时间
     *
     * @param orderId 订单ID
     * @param amount  退款金额
     */
    @PostMapping("/inner/order/pay/refund")
    void markRefunded(@RequestParam("orderId") Long orderId, @RequestParam("amount") BigDecimal amount);
}

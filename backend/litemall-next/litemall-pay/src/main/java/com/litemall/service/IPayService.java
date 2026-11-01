package com.litemall.service;

import com.litemall.domain.DTO.RefundDTO;
import com.litemall.domain.VO.PayParamsVO;
import com.litemall.domain.VO.PayResultVO;
import com.litemall.domain.VO.RefundVO;
import com.litemall.domain.dto.PayRefundDTO;
import com.litemall.domain.dto.PayRefundResultDTO;

/**
 * <p>
 * 支付服务
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public interface IPayService {

    /**
     * 微信支付下单（开发环境模拟支付），等价于 {@code wechatPay(orderId, null)}
     *
     * @param orderId 订单ID
     * @return 小程序 wx.requestPayment 参数
     */
    PayParamsVO wechatPay(Long orderId);

    /**
     * 微信支付下单（CI-103：演示支付，可选成功/失败）
     *
     * @param orderId 订单ID
     * @param mode    演示结果：success（默认）/ fail
     * @return 小程序 wx.requestPayment 参数（含 demo / payResult）
     */
    PayParamsVO wechatPay(Long orderId, String mode);

    /**
     * 微信支付成功回调（H2：先验签、再幂等去重、后通知订单服务）
     *
     * @param serial    Wechatpay-Serial 头
     * @param timestamp Wechatpay-Timestamp 头
     * @param nonce     Wechatpay-Nonce 头
     * @param signature Wechatpay-Signature 头
     * @param rawBody   原始回调报文
     */
    void notifyWechat(String serial, String timestamp, String nonce, String signature, String rawBody);

    /**
     * 申请退款（CI-105：保留，语义收窄为「仅演示直付单可用」；R1 售后退款一律走 CN-07）
     *
     * @param dto 退款请求（金额为整数分）
     * @return 退款单ID与状态
     */
    RefundVO refund(RefundDTO dto);

    /**
     * 售后退款（CN-IN-02：供 order 服务内部调用，幂等键 aftersaleId）
     *
     * @param dto 退款请求
     * @return 退款结果（status=1 退款成功）
     */
    PayRefundResultDTO refundFunds(PayRefundDTO dto);

    /**
     * 查询支付结果
     *
     * @param orderId 订单ID
     * @return 支付结果
     */
    PayResultVO queryResult(Long orderId);
}
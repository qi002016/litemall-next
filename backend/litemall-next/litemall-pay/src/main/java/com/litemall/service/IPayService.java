package com.litemall.service;

import com.litemall.domain.DTO.RefundDTO;
import com.litemall.domain.VO.PayParamsVO;
import com.litemall.domain.VO.PayResultVO;
import com.litemall.domain.VO.RefundVO;

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
     * 微信支付下单（开发环境模拟支付）
     *
     * @param orderId 订单ID
     * @return 小程序 wx.requestPayment 参数
     */
    PayParamsVO wechatPay(Long orderId);

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
     * 申请退款
     *
     * @param dto 退款请求
     * @return 退款单ID与状态
     */
    RefundVO refund(RefundDTO dto);

    /**
     * 查询支付结果
     *
     * @param orderId 订单ID
     * @return 支付结果
     */
    PayResultVO queryResult(Long orderId);
}

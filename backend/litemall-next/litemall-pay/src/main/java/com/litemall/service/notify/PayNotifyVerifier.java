package com.litemall.service.notify;

/**
 * H2：支付回调验签器。
 * <p>
 * 实现类：
 * <ul>
 *   <li>{@link WechatPayV3NotifyVerifier} —— 生产实现，微信平台证书验签 + APIv3 密钥 AES-GCM 解密，
 *       由 {@code hm.pay.wechat.enabled=true} 开启；</li>
 *   <li>{@link MockPayNotifyVerifier} —— 仅 dev 生效的明文实现。</li>
 * </ul>
 * 非 dev 且未开启真实验签时容器中无本接口 bean，支付服务启动失败（fail-fast），
 * 杜绝"未验签即置已支付"。
 */
public interface PayNotifyVerifier {

    /**
     * 校验回调签名并解析出订单信息。
     *
     * @param serial    Wechatpay-Serial 头（平台证书序列号）
     * @param timestamp Wechatpay-Timestamp 头
     * @param nonce     Wechatpay-Nonce 头
     * @param signature Wechatpay-Signature 头
     * @param rawBody   原始回调报文
     * @return 归一化结果
     * @throws com.litemall.exception.BadRequestException 验签失败或报文非法
     */
    NotifyResult verify(String serial, String timestamp, String nonce, String signature, String rawBody);
}

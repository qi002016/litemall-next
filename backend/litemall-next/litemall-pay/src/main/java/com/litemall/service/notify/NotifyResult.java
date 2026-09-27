package com.litemall.service.notify;

/**
 * H2：支付回调验签结果（已归一化的订单信息）。
 *
 * @param orderId      订单ID（微信侧 out_trade_no）
 * @param payId        微信支付单号（transaction_id），幂等去重键
 * @param totalFeeCents 微信回调金额（分）；mock 实现可为 null（跳过金额校验）
 */
public record NotifyResult(Long orderId, String payId, Integer totalFeeCents) {
}

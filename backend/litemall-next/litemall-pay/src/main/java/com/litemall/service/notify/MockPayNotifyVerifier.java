package com.litemall.service.notify;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.litemall.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * H2：dev 专用明文验签器（不做任何签名校验，仅解析明文报文）。
 * <p>
 * 仅当 profile=dev 时注册；生产 profile 下不存在本 bean，
 * 必须开启 {@code hm.pay.wechat.enabled=true} 的 {@link WechatPayV3NotifyVerifier}，
 * 否则支付服务启动失败。
 */
@Slf4j
@Component
@Profile("dev")
public class MockPayNotifyVerifier implements PayNotifyVerifier {

    @Override
    public NotifyResult verify(String serial, String timestamp, String nonce, String signature, String rawBody) {
        log.warn("[H2] dev 明文回调解析(hm.pay.wechat 未启用真实验签)，rawBody 长度={}",
                rawBody == null ? 0 : rawBody.length());
        if (rawBody == null || !JSONUtil.isTypeJSONObject(rawBody)) {
            throw new BadRequestException("回调报文非法");
        }
        JSONObject json = JSONUtil.parseObj(rawBody);
        Long orderId = json.getLong("orderId");
        String payId = json.getStr("payId");
        if (orderId == null) {
            throw new BadRequestException("回调缺少订单号");
        }
        return new NotifyResult(orderId, payId, null);
    }
}

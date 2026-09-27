package com.litemall.service.notify;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.litemall.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H2：微信支付 APIv3 回调验签器（平台证书 RSA-SHA256 验签 + APIv3 密钥 AES-256-GCM 解密）。
 * <p>
 * 由 {@code hm.pay.wechat.enabled=true} 开启；所需证书与密钥全部来自环境变量，不入仓库：
 * <ul>
 *   <li>{@code HM_WX_PAY_CERT}：微信支付平台证书（PEM，支持 classpath:/file: 前缀）</li>
 *   <li>{@code HM_WX_PAY_APIV3_KEY}：APIv3 密钥（32 字节）</li>
 * </ul>
 * 校验失败一律抛 {@link BadRequestException}，订单状态不会被触碰。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "hm.pay.wechat", name = "enabled", havingValue = "true")
public class WechatPayV3NotifyVerifier implements PayNotifyVerifier {

    /** 时间戳容差：超出即判定为重放 */
    private static final Duration TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);
    private static final int GCM_TAG_BITS = 128;
    /** 微信回调允许的最大报文长度（字符），防超大 body DoS */
    private static final int MAX_BODY_LENGTH = 64 * 1024;

    private final String apiV3Key;
    /** 平台证书序列号 → 公钥 */
    private final Map<String, PublicKey> certSerialToKey = new ConcurrentHashMap<>();

    public WechatPayV3NotifyVerifier(WechatPayProperties properties) {
        if (StrUtil.isBlank(properties.getApiV3Key())) {
            throw new IllegalStateException("H2：hm.pay.wechat.enabled=true 但缺少 hm.pay.wechat.apiV3Key(HM_WX_PAY_APIV3_KEY)");
        }
        if (properties.getPlatformCert() == null) {
            throw new IllegalStateException("H2：hm.pay.wechat.enabled=true 但缺少 hm.pay.wechat.platformCert(HM_WX_PAY_CERT)");
        }
        this.apiV3Key = properties.getApiV3Key();
        loadCertificate(properties.getPlatformCert());
    }

    private void loadCertificate(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) factory.generateCertificate(in);
            String serial = cert.getSerialNumber().toString(16).toUpperCase();
            certSerialToKey.put(serial, cert.getPublicKey());
            log.info("[H2] 微信支付平台证书已加载，serial={}", serial);
        } catch (Exception e) {
            throw new IllegalStateException("H2：微信支付平台证书加载失败：" + e.getMessage(), e);
        }
    }

    @Override
    public NotifyResult verify(String serial, String timestamp, String nonce, String signature, String rawBody) {
        // 1.必填头校验
        if (StrUtil.hasBlank(serial, timestamp, nonce, signature) || StrUtil.isBlank(rawBody)) {
            throw new BadRequestException("回调缺少验签参数");
        }
        if (rawBody.length() > MAX_BODY_LENGTH) {
            throw new BadRequestException("回调报文过大");
        }
        // 2.时间戳防重放
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BadRequestException("回调时间戳非法");
        }
        if (Math.abs(Instant.now().getEpochSecond() - ts) > TIMESTAMP_TOLERANCE.getSeconds()) {
            throw new BadRequestException("回调已过期，疑似重放");
        }
        // 3.平台证书验签：SHA256withRSA(timestamp\nnonce\nbody\n)
        PublicKey publicKey = certSerialToKey.get(serial);
        if (publicKey == null) {
            throw new BadRequestException("未知的平台证书序列号");
        }
        String message = timestamp + "\n" + nonce + "\n" + rawBody + "\n";
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.decode(signature))) {
                throw new BadRequestException("回调验签失败");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[H2] 回调验签异常", e);
            throw new BadRequestException("回调验签失败");
        }
        // 4.解密 resource（AES-256-GCM）
        JSONObject body = JSONUtil.parseObj(rawBody);
        JSONObject resource = body.getJSONObject("resource");
        if (resource == null) {
            throw new BadRequestException("回调缺少 resource");
        }
        String plaintext = decrypt(resource.getStr("ciphertext"), resource.getStr("nonce"),
                resource.getStr("associated_data"));
        JSONObject txn = JSONUtil.parseObj(plaintext);
        // 5.事件与支付状态校验
        if (!"TRANSACTION.SUCCESS".equals(body.getStr("event_type"))) {
            throw new BadRequestException("非支付成功事件");
        }
        if (!"SUCCESS".equals(txn.getStr("trade_state"))) {
            throw new BadRequestException("交易未成功");
        }
        Long orderId = parseOrderId(txn.getStr("out_trade_no"));
        String transactionId = txn.getStr("transaction_id");
        Integer total = txn.getJSONObject("amount") == null ? null : txn.getJSONObject("amount").getInt("total");
        return new NotifyResult(orderId, transactionId, total);
    }

    private Long parseOrderId(String outTradeNo) {
        if (StrUtil.isBlank(outTradeNo)) {
            throw new BadRequestException("回调缺少商户订单号");
        }
        try {
            return Long.valueOf(outTradeNo);
        } catch (NumberFormatException e) {
            throw new BadRequestException("商户订单号非法");
        }
    }

    private String decrypt(String ciphertext, String nonce, String associatedData) {
        if (StrUtil.hasBlank(ciphertext, nonce)) {
            throw new BadRequestException("回调密文不完整");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec key = new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            if (StrUtil.isNotBlank(associatedData)) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            return new String(cipher.doFinal(Base64.decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[H2] 回调解密失败", e);
            throw new BadRequestException("回调解密失败");
        }
    }
}

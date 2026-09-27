package com.litemall.notify;

import lombok.extern.slf4j.Slf4j;

/**
 * 腾讯云短信 Sender(占位实现)。
 * <p>
 * 使用方式:
 * 1. 在 litemall-common/pom.xml 加入腾讯云短信 SDK:
 * <pre>
 * &lt;dependency&gt;
 *   &lt;groupId&gt;com.tencentcloudapi&lt;/groupId&gt;
 *   &lt;artifactId&gt;tencentcloud-sdk-java-sms&lt;/artifactId&gt;
 *   &lt;version&gt;3.1.0&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 * 2. 在 application.yml 配置 litemall.notify.sms.active=tencent
 * 3. 本类会自动装配。
 * <p>
 * 腾讯云模板参数直接传数组,无需转 JSON。
 */
@Slf4j
public class TencentSmsSender implements SmsSender {

    private final String secretId;
    private final String secretKey;
    private final String sdkAppId;
    private final String signName;
    private final String region;

    public TencentSmsSender(String secretId, String secretKey, String sdkAppId, String signName, String region) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.sdkAppId = sdkAppId;
        this.signName = signName;
        this.region = region;
    }

    @Override
    public SmsResult send(String phone, String content) {
        log.warn("[TencentSmsSender] 腾讯云不支持裸内容短信,请使用 sendWithTemplate。phone={}, content={}", phone, content);
        return SmsResult.fail("not_supported");
    }

    @Override
    public SmsResult sendWithTemplate(String phone, String templateId, String[] params) {
        // TODO: 引入腾讯云 SDK 后替换为真实调用:
        //
        // Credential cred = new Credential(secretId, secretKey);
        // Client client = new Client(cred, region, new HttpProfile());
        // SendSmsRequest req = new SendSmsRequest();
        // req.setPhoneNumberSet(List.of("+86" + phone));
        // req.setSmsSdkAppId(sdkAppId);
        // req.setSignName(signName);
        // req.setTemplateId(templateId);
        // req.setTemplateParamSet(List.of(params));
        // SendSmsResponse resp = client.SendSms(req);
        // return resp.getResponse().getSendStatusSet().get(0).getCode().equals("Ok")
        //     ? SmsResult.ok(resp) : SmsResult.fail(resp);
        log.info("[TencentSmsSender] (占位,未接入SDK) phone={}, templateId={}, params={}", phone, templateId, (Object) params);
        return SmsResult.ok("stub");
    }
}

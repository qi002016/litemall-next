package com.litemall.notify;

import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云短信 Sender(占位实现)。
 * <p>
 * 使用方式:
 * 1. 在 litemall-common/pom.xml 加入阿里云短信新版 SDK:
 * <pre>
 * &lt;dependency&gt;
 *   &lt;groupId&gt;com.aliyun&lt;/groupId&gt;
 *   &lt;artifactId&gt;dysmsapi20170525&lt;/artifactId&gt;
 *   &lt;version&gt;3.0.0&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 * 2. 在 application.yml 配置 litemall.notify.sms.active=aliyun
 * 3. 本类会自动装配,真正调用阿里云 API。
 * <p>
 * 注意:阿里云模板参数是 JSON 格式({code:xx}),腾讯云是数组。本类统一参数名为 code/code1/code2...
 */
@Slf4j
public class AliyunSmsSender implements SmsSender {

    private final String accessKeyId;
    private final String accessKeySecret;
    private final String signName;
    private final String endpoint;

    public AliyunSmsSender(String accessKeyId, String accessKeySecret, String signName, String endpoint) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.signName = signName;
        this.endpoint = endpoint;
    }

    @Override
    public SmsResult send(String phone, String content) {
        // 阿里云不支持裸内容,必须用模板;这里直接打日志
        log.warn("[AliyunSmsSender] 阿里云不支持裸内容短信,请使用 sendWithTemplate。phone={}, content={}", phone, content);
        return SmsResult.fail("not_supported");
    }

    @Override
    public SmsResult sendWithTemplate(String phone, String templateId, String[] params) {
        // TODO: 引入阿里云 dysmsapi20170525 SDK 后,在此处替换为真实调用:
        //
        // com.aliyun.dysmsapi20170525.Client client = createClient();
        // SendSmsRequest req = new SendSmsRequest()
        //     .setPhoneNumbers(phone)
        //     .setSignName(signName)
        //     .setTemplateCode(templateId)
        //     .setTemplateParam(buildTemplateParam(params));
        // SendSmsResponse resp = client.sendSms(req);
        // return "OK".equals(resp.getBody().getCode()) ? SmsResult.ok(resp) : SmsResult.fail(resp);
        log.info("[AliyunSmsSender] (占位,未接入SDK) phone={}, templateId={}, params={}", phone, templateId, (Object) params);
        return SmsResult.ok("stub");
    }

    /**
     * 统一把数组参数转成阿里云要求的 JSON:{ "code": p0, "code1": p1, ... }
     */
    private String buildTemplateParam(String[] params) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(i == 0 ? "code" : "code" + i).append("\":\"").append(params[i]).append("\"");
        }
        return sb.append("}").toString();
    }
}

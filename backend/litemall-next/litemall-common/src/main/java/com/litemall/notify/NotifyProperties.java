package com.litemall.notify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知服务配置项,前缀 litemall.notify
 * <p>
 * 示例 application.yml:
 * <pre>
 * litemall:
 *   notify:
 *     sms:
 *       enabled: true
 *       active: aliyun        # aliyun / tencent / noop
 *       sign: "Litemall商城"
 *       template:
 *         - name: captcha
 *           templateId: SMS_123456
 *         - name: paySucceed
 *           templateId: SMS_789012
 *       aliyun:
 *         accessKeyId: xxx
 *         accessKeySecret: xxx
 *         endpoint: dysmsapi.aliyuncs.com
 *       tencent:
 *         secretId: xxx
 *         secretKey: xxx
 *         sdkAppId: 1400000000
 *         region: ap-guangzhou
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "litemall.notify")
public class NotifyProperties {

    private Sms sms = new Sms();

    @Data
    public static class Sms {
        /** 是否启用短信;false 时自动降级为 NoopSmsSender */
        private boolean enabled = false;

        /** 厂商:aliyun / tencent / noop */
        private String active = "noop";

        /** 短信签名 */
        private String sign;

        /** 模板列表 */
        private List<Template> template = new ArrayList<>();

        /** 阿里云配置 */
        private Aliyun aliyun = new Aliyun();

        /** 腾讯云配置 */
        private Tencent tencent = new Tencent();

        @Data
        public static class Template {
            /** 对应 NotifyType.getType() */
            private String name;
            /** 云厂商控制台申请的模板ID */
            private String templateId;
        }

        @Data
        public static class Aliyun {
            private String accessKeyId;
            private String accessKeySecret;
            private String endpoint = "dysmsapi.aliyuncs.com";
        }

        @Data
        public static class Tencent {
            private String secretId;
            private String secretKey;
            private String sdkAppId;
            private String region = "ap-guangzhou";
        }
    }
}

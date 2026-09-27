package com.litemall.notify.config;

import com.litemall.notify.AliyunSmsSender;
import com.litemall.notify.NoopSmsSender;
import com.litemall.notify.NotifyProperties;
import com.litemall.notify.NotifyService;
import com.litemall.notify.SmsSender;
import com.litemall.notify.TencentSmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 通知服务自动装配。
 * <p>
 * 根据 application.yml 中 litemall.notify.sms.active 的值自动选择 SmsSender 实现:
 * <ul>
 *   <li>aliyun → AliyunSmsSender</li>
 *   <li>tencent → TencentSmsSender</li>
 *   <li>其他 / 未启用 → NoopSmsSender(只打日志,不真正发送)</li>
 * </ul>
 * <p>
 * 本类通过 META-INF/spring/...imports 自动加载,业务服务无需手动 @Import。
 */
@AutoConfiguration
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NotifyAutoConfiguration.class);

    @Bean
    public SmsSender smsSender(NotifyProperties properties) {
        NotifyProperties.Sms sms = properties.getSms();
        if (!sms.isEnabled()) {
            log.info("[Notify] 短信未启用(litemall.notify.sms.enabled=false),使用 NoopSmsSender");
            return new NoopSmsSender();
        }
        return switch (sms.getActive() == null ? "noop" : sms.getActive().toLowerCase()) {
            case "aliyun" -> {
                NotifyProperties.Sms.Aliyun a = sms.getAliyun();
                log.info("[Notify] 使用阿里云短信,endpoint={}", a.getEndpoint());
                yield new AliyunSmsSender(a.getAccessKeyId(), a.getAccessKeySecret(), sms.getSign(), a.getEndpoint());
            }
            case "tencent" -> {
                NotifyProperties.Sms.Tencent t = sms.getTencent();
                log.info("[Notify] 使用腾讯云短信,region={}, appId={}", t.getRegion(), t.getSdkAppId());
                yield new TencentSmsSender(t.getSecretId(), t.getSecretKey(), t.getSdkAppId(), sms.getSign(), t.getRegion());
            }
            default -> {
                log.info("[Notify] 未识别短信厂商 active={},使用 NoopSmsSender", sms.getActive());
                yield new NoopSmsSender();
            }
        };
    }

    @Bean
    public NotifyService notifyService(SmsSender smsSender, NotifyProperties properties) {
        return new NotifyService(smsSender, properties.getSms().getTemplate());
    }
}

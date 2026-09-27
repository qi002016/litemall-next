package com.litemall.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Map;

/**
 * 通知服务门面
 * <p>
 * 业务代码只面向本类,不关心底层是阿里云还是腾讯云。
 * 用法:
 * <pre>
 * &#64;RequiredArgsConstructor
 * &#64;Service
 * public class LoginService {
 *     private final NotifyService notifyService;
 *
 *     public void sendSmsCode(String phone, String code) {
 *         notifyService.notifySmsTemplate(phone, NotifyType.CAPTCHA, new String[]{code});
 *     }
 * }
 * </pre>
 */
@Slf4j
public class NotifyService {

    private final SmsSender smsSender;
    private final List<NotifyProperties.Sms.Template> templates;

    public NotifyService(SmsSender smsSender, List<NotifyProperties.Sms.Template> templates) {
        this.smsSender = smsSender;
        this.templates = templates;
    }

    /**
     * 异步发送模板短信。
     * <p>
     * 注意:@Async 需要业务服务启动类加 @EnableAsync 才生效;未开启时本方法退化为同步调用。
     * 发货通知、支付成功通知等"调用方不关心结果"的场景用这个。
     */
    @Async
    public void notifySmsTemplate(String phone, NotifyType notifyType, String[] params) {
        try {
            SmsResult result = doSend(phone, notifyType, params);
            if (!result.successful()) {
                log.warn("短信发送失败 phone={}, type={}, result={}", phone, notifyType, result.result());
            }
        } catch (Exception e) {
            log.error("短信发送异常 phone={}, type={}", phone, notifyType, e);
        }
    }

    /**
     * 同步发送模板短信,返回发送结果。
     * 登录/注册验证码场景用这个——需要确认短信是否发出去了。
     */
    public SmsResult notifySmsTemplateSync(String phone, NotifyType notifyType, String[] params) {
        return doSend(phone, notifyType, params);
    }

    private SmsResult doSend(String phone, NotifyType notifyType, String[] params) {
        String templateId = resolveTemplateId(notifyType);
        if (templateId == null) {
            log.warn("未配置通知类型 {} 的模板ID,跳过发送", notifyType);
            return SmsResult.fail("template_not_configured");
        }
        return smsSender.sendWithTemplate(phone, templateId, params);
    }

    private String resolveTemplateId(NotifyType type) {
        if (templates == null) return null;
        for (NotifyProperties.Sms.Template t : templates) {
            if (type.getType().equals(t.getName())) {
                return t.getTemplateId();
            }
        }
        return null;
    }
}

package com.litemall.notify;

import lombok.extern.slf4j.Slf4j;

/**
 * 空短信实现:不真正发送短信,仅打印日志。
 * <p>
 * 用于本地开发、测试环境未配置云厂商密钥时兜底,业务代码无需判空。
 */
@Slf4j
public class NoopSmsSender implements SmsSender {

    @Override
    public SmsResult send(String phone, String content) {
        log.info("[NoopSmsSender] 发送短信到 {}: {}", phone, content);
        return SmsResult.ok("noop");
    }

    @Override
    public SmsResult sendWithTemplate(String phone, String templateId, String[] params) {
        log.info("[NoopSmsSender] 发送模板短信 phone={}, templateId={}, params={}",
                phone, templateId, (Object) params);
        return SmsResult.ok("noop");
    }
}

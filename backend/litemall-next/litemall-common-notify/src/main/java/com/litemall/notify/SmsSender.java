package com.litemall.notify;

/**
 * 短信发送策略接口
 * <p>
 * 不同云厂商(阿里云/腾讯云)各实现一份;业务代码只面向本接口,不关心厂商。
 */
public interface SmsSender {

    /**
     * 发送裸内容短信(不推荐,国内短信强制要求模板)
     */
    SmsResult send(String phone, String content);

    /**
     * 通过模板发送短信
     *
     * @param phone      手机号
     * @param templateId 模板ID(在云厂商控制台申请)
     * @param params     模板参数,如 ["123456"] 对应模板 "您的验证码是{code}"
     */
    SmsResult sendWithTemplate(String phone, String templateId, String[] params);
}

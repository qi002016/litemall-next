package com.litemall.notify;

/**
 * 通知类型枚举
 * <p>
 * 对应 application.yml 中 litemall.notify.sms.template 配置列表里的 name 字段。
 * 新增通知场景时,先在这里加枚举,再去配置文件加对应模板ID。
 */
public enum NotifyType {

    /** 支付成功 */
    PAY_SUCCEED("paySucceed"),

    /** 发货通知 */
    SHIP("ship"),

    /** 退款通知 */
    REFUND("refund"),

    /** 登录/注册验证码 */
    CAPTCHA("captcha");

    private final String type;

    NotifyType(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}

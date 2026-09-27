package com.litemall.notify;

/**
 * 短信发送结果
 *
 * @param successful 是否发送成功
 * @param result     云厂商原始响应(便于排查问题)
 */
public record SmsResult(boolean successful, Object result) {

    public static SmsResult ok(Object result) {
        return new SmsResult(true, result);
    }

    public static SmsResult fail(Object result) {
        return new SmsResult(false, result);
    }
}

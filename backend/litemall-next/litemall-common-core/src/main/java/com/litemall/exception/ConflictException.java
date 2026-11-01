package com.litemall.exception;

/**
 * 状态冲突（409）。
 * <p>
 * R1 用法（03 §4.3/§4.5）：状态跃迁 CAS 失败（affected = 0）、幂等键复用但参数不同、
 * 库存不足、售后未完结等「当前状态不允许该动作」。文案必须用户可读；
 * {@code errorCode} 用于前端差异化提示。
 * </p>
 */
public class ConflictException extends CommonException {

    public ConflictException(String message) {
        super(message, 409, null);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause, 409, null);
    }

    /**
     * @param errorCode 细分码，如 {@code ORDER_STATUS_ILLEGAL} / {@code IDEMPOTENT_CONFLICT}
     */
    public ConflictException(String message, String errorCode) {
        super(message, 409, errorCode);
    }
}
package com.litemall.exception;

import lombok.Getter;

/**
 * 业务异常基类。
 * <p>
 * R1（03 §4.1）三层错误结构：<b>HTTP</b>（对外端点恒 200、对内端点保留非 200）／
 * <b>{@code Result.code}</b>（200/400/401/403/404/409/429/500）／
 * <b>{@code Result.data.errorCode}</b>（域内细分码，仅用于前端差异化文案）。
 * 因此除 {@link #code} 外新增 {@link #errorCode}（可空）：
 * 为空时失败响应的 {@code data} 为 {@code null}；非空时 {@code data} 严格为
 * {@code {"errorCode":"XXX"}}，不得附加其它键（03 §4.1）。
 * </p>
 */
@Getter
public class CommonException extends RuntimeException {

    /** 业务码，映射 {@code Result.code} */
    private final int code;

    /** 域内细分码（可空），落 {@code Result.data.errorCode} */
    private final String errorCode;

    public CommonException(String message, int code) {
        this(message, code, null);
    }

    public CommonException(String message, int code, String errorCode) {
        super(message);
        this.code = code;
        this.errorCode = errorCode;
    }

    public CommonException(String message, Throwable cause, int code) {
        this(message, cause, code, null);
    }

    public CommonException(String message, Throwable cause, int code, String errorCode) {
        super(message, cause);
        this.code = code;
        this.errorCode = errorCode;
    }

    public CommonException(Throwable cause, int code) {
        this(cause, code, null);
    }

    public CommonException(Throwable cause, int code, String errorCode) {
        super(cause);
        this.code = code;
        this.errorCode = errorCode;
    }
}
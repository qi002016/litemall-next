package com.litemall.domain;

import com.litemall.exception.CommonException;
import lombok.Data;

import java.util.Map;


@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "OK", data);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /**
     * R1（03 §4.1）：失败响应 {@code data} 只允许 null 或严格 {@code {"errorCode":"XXX"}}，
     * 禁止附加其它键，也禁止把 errorCode 提升为顶层字段。
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> error(CommonException e) {
        T data = e.getErrorCode() == null ? null : (T) Map.of("errorCode", e.getErrorCode());
        return new Result<>(e.getCode(), e.getMessage(), data);
    }

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public boolean success(){
        return code == 200;
    }
}

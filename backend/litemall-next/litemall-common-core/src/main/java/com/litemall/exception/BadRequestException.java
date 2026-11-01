package com.litemall.exception;

public class BadRequestException extends CommonException{

    /** 参数类通用细分码（03 §4.3：参数非法一律 PARAM_INVALID，禁自造 XXX_INVALID） */
    public static final String PARAM_INVALID = "PARAM_INVALID";

    public BadRequestException(String message) {
        super(message, 400);
    }

    /** 带域内细分码（如 ADDRESS_INVALID / COUPON_NOT_USABLE / ORDER_ITEM_LIMIT） */
    public BadRequestException(String message, String errorCode) {
        super(message, 400, errorCode);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause, 400);
    }

    public BadRequestException(Throwable cause) {
        super(cause, 400);
    }
}
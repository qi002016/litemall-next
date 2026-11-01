package com.litemall.exception;

/**
 * 资源不存在（404）。
 * <p>
 * R1 用法（03 §4.3/§4.5）：
 * <ul>
 *   <li>对外端点：「订单不存在」「售后单不存在」等，含<b>他人资源</b>——详情类不得用 403 泄露存在性；</li>
 *   <li>对内端点（{@code /inner/**}）：HTTP 404 + {@code RESOURCE_NOT_FOUND}（D-22）。</li>
 * </ul>
 */
public class NotFoundException extends CommonException {

    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";

    public NotFoundException(String message) {
        super(message, 404, RESOURCE_NOT_FOUND);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause, 404, RESOURCE_NOT_FOUND);
    }

    /**
     * @param errorCode 细分码，如 {@code ORDER_NOT_FOUND}（订单域用）
     */
    public NotFoundException(String message, String errorCode) {
        super(message, 404, errorCode);
    }
}
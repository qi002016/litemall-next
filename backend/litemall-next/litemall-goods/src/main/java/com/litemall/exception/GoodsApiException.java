package com.litemall.exception;

import com.litemall.constant.GoodsErrorCode;

/**
 * 商品域业务异常：携带 03 §4.3 冻结的 {@code errorCode}。
 *
 * <p>
 * 对外（{@code /app/**}）由 {@link com.litemall.advice.GoodsApiExceptionAdvice} 统一转成
 * <b>HTTP 200</b> + {@code Result{code, msg, data:{"errorCode":...}}}（06 §1.2.1 口径）；
 * 对内（{@code /inner/**}）不经过该 advice，仍按裸类型 + 非 200 HTTP 返回。
 * </p>
 *
 * <p>
 * 继承 {@link CommonException} 是为了复用公共层的 code 语义与日志，
 * 但<b>不修改</b>公共层（{@code CommonExceptionAdvice} 属 R1-02 单一所有者）。
 * </p>
 */
public class GoodsApiException extends CommonException {

    private final String errorCode;

    public GoodsApiException(String errorCode, String message, int code) {
        super(message, code);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /* ==================== 常用工厂 ==================== */

    public static GoodsApiException paramInvalid(String message) {
        return new GoodsApiException(GoodsErrorCode.PARAM_INVALID, message, 400);
    }

    public static GoodsApiException notFound(String message) {
        return new GoodsApiException(GoodsErrorCode.RESOURCE_NOT_FOUND, message, 404);
    }

    public static GoodsApiException goodsOffSale() {
        return new GoodsApiException(GoodsErrorCode.GOODS_OFF_SALE, "商品已下架", 404);
    }

    public static GoodsApiException goodsInUse() {
        return new GoodsApiException(GoodsErrorCode.GOODS_IN_USE, "该商品已产生订单，不能删除，请改为下架", 409);
    }

    public static GoodsApiException statusOnSaleConflict() {
        return new GoodsApiException(GoodsErrorCode.STATUS_ON_SALE_CONFLICT,
                "status 与 onSale 矛盾：在售=1 必须 onSale=true，草稿/下架必须 onSale=false", 400);
    }

    public static GoodsApiException skuCodeDuplicated(String skuCode) {
        return new GoodsApiException(GoodsErrorCode.SKU_CODE_DUPLICATED, "SKU 编码已存在：" + skuCode, 409);
    }

    public static GoodsApiException skuInUse() {
        return new GoodsApiException(GoodsErrorCode.SKU_IN_USE, "该规格已产生订单，不能删除", 409);
    }

    /**
     * SKU 已被订单引用（编辑时删除该规格行）。msg 用于给出「只能把库存置 0」的处置建议。
     */
    public static GoodsApiException skuInUse(String message) {
        return new GoodsApiException(GoodsErrorCode.SKU_IN_USE, message, 409);
    }

    public static GoodsApiException stockAdjustConflict(Integer locked) {
        return new GoodsApiException(GoodsErrorCode.STOCK_ADJUST_CONFLICT,
                "当前已锁 " + (locked == null ? 0 : locked) + " 件，库存不能低于该值", 400);
    }

    public static GoodsApiException concurrentModified() {
        return new GoodsApiException(GoodsErrorCode.CONCURRENT_MODIFIED, "该商品已被修改，请刷新", 409);
    }
}
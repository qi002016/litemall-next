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

    /**
     * 并发修改（分类/品牌）：05 §1.3 冻结提示语「内容已被他人修改，请刷新后重试」。
     *
     * @param message 提示语
     * @return 409 {@code CONCURRENT_MODIFIED}
     */
    public static GoodsApiException concurrentModified(String message) {
        return new GoodsApiException(GoodsErrorCode.CONCURRENT_MODIFIED, message, 409);
    }

    /* ==================== R2：分类（CN-21~24） ==================== */

    /** 同级下分类名重复（409，唯一键 {@code uk_category_name} 兜底） */
    public static GoodsApiException categoryNameDuplicated() {
        return new GoodsApiException(GoodsErrorCode.CATEGORY_NAME_DUPLICATED, "同级下已存在同名分类", 409);
    }

    /** 分类下仍有子分类（409） */
    public static GoodsApiException categoryHasChildren() {
        return new GoodsApiException(GoodsErrorCode.CATEGORY_HAS_CHILDREN, "该分类下仍有子分类，不能删除", 409);
    }

    /** 分类（含子分类）下仍有商品（409，msg 逐字冻结） */
    public static GoodsApiException categoryInUse() {
        return new GoodsApiException(GoodsErrorCode.CATEGORY_IN_USE, "请先移走商品", 409);
    }

    /** 分类不存在（404，CN-23 / CN-24） */
    public static GoodsApiException categoryNotFound() {
        return new GoodsApiException(GoodsErrorCode.RESOURCE_NOT_FOUND, "分类不存在", 404);
    }

    /** 父分类不存在或已删除（404，CN-22；05 §3.2 冻结文案与「分类不存在」区分） */
    public static GoodsApiException parentCategoryNotFound() {
        return new GoodsApiException(GoodsErrorCode.RESOURCE_NOT_FOUND, "父分类不存在", 404);
    }

    /* ==================== R2：品牌（CN-25~28） ==================== */

    /** 品牌名重复（409，唯一键 {@code uk_brand_name} 兜底） */
    public static GoodsApiException brandNameDuplicated() {
        return new GoodsApiException(GoodsErrorCode.BRAND_NAME_DUPLICATED, "品牌名已存在", 409);
    }

    /** 品牌下仍有商品（409，msg 逐字冻结） */
    public static GoodsApiException brandInUse() {
        return new GoodsApiException(GoodsErrorCode.BRAND_IN_USE, "请先移走商品", 409);
    }

    /** 品牌不存在（404） */
    public static GoodsApiException brandNotFound() {
        return new GoodsApiException(GoodsErrorCode.RESOURCE_NOT_FOUND, "品牌不存在", 404);
    }

    /* ==================== R2：评价（CI-35/CI-36、CN-29~31） ==================== */

    /** 该订单行已评价（409；msg 逐字，05 §2.3.3 步 6） */
    public static GoodsApiException commentAlreadyExists() {
        return new GoodsApiException(GoodsErrorCode.COMMENT_ALREADY_EXISTS, "该订单行已评价", 409);
    }

    /** 无权评价他人订单（403；msg 逐字，05 §2.3.3 步 3） */
    public static GoodsApiException notOrderOwner() {
        return new GoodsApiException(GoodsErrorCode.NOT_ORDER_OWNER, "无权评价他人订单", 403);
    }

    /** 订单未收货（400；msg 逐字，05 §2.3.3 步 5） */
    public static GoodsApiException orderGoodsNotReceived() {
        return new GoodsApiException(GoodsErrorCode.ORDER_GOODS_NOT_RECEIVED, "仅已收货订单可评价", 400);
    }

    /** 订单行已退款（400；msg 逐字，05 §2.3.3 步 7） */
    public static GoodsApiException orderGoodsRefunded() {
        return new GoodsApiException(GoodsErrorCode.ORDER_GOODS_REFUNDED, "该商品已退款，不可评价", 400);
    }

    /** 评价状态并发冲突（409；msg 逐字，05 §3.11） */
    public static GoodsApiException commentStatusConflict() {
        return new GoodsApiException(GoodsErrorCode.COMMENT_STATUS_CONFLICT, "评价状态已变更，请刷新后重试", 409);
    }

    /** 评价不存在（404；msg 逐字，05 §3.10/§3.11） */
    public static GoodsApiException commentNotFound() {
        return new GoodsApiException(GoodsErrorCode.RESOURCE_NOT_FOUND, "评价不存在", 404);
    }
}
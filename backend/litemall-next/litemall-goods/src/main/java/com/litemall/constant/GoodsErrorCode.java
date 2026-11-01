package com.litemall.constant;

/**
 * 商品/库存域错误码（03 §4.3 冻结的 R1 子集，共 8 个 + 3 个通用码）。
 *
 * <p>
 * 只登记 03 §4.3「R1 实际使用的 27 个码」中属于本域的码。08 §11 里的
 * {@code CATEGORY_* / BRAND_* / COMMENT_*} 等属 R2/R3，<b>禁止</b>在 R1 自造；
 * 这类参数问题统一用通用 {@code PARAM_INVALID} + 中文 msg 表达。
 * </p>
 */
public final class GoodsErrorCode {

    /** 通用：参数非法（400） */
    public static final String PARAM_INVALID = "PARAM_INVALID";

    /** 通用：资源不存在（404） */
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";

    /** 商品未上架/草稿（404，CI-32） */
    public static final String GOODS_OFF_SALE = "GOODS_OFF_SALE";

    /** 商品被引用不可删（409，CI-12） */
    public static final String GOODS_IN_USE = "GOODS_IN_USE";

    /** status 与 onSale 矛盾（400，CI-10/11） */
    public static final String STATUS_ON_SALE_CONFLICT = "STATUS_ON_SALE_CONFLICT";

    /** sku_code 全局重复（409，CI-10/11） */
    public static final String SKU_CODE_DUPLICATED = "SKU_CODE_DUPLICATED";

    /** SKU 被订单引用不可删（409，CI-11） */
    public static final String SKU_IN_USE = "SKU_IN_USE";

    /** 库存调整值低于锁定数（400，CN-14） */
    public static final String STOCK_ADJUST_CONFLICT = "STOCK_ADJUST_CONFLICT";

    /** 乐观锁/updateTime/expectNumber 不一致（409，CI-11、CN-14） */
    public static final String CONCURRENT_MODIFIED = "CONCURRENT_MODIFIED";

    private GoodsErrorCode() {
    }
}
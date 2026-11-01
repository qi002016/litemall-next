package com.litemall.constant;

/**
 * 商品/库存域错误码。
 *
 * <p>
 * R1 只登记 03 §4.3 冻结的 8 个域码 + 2 个通用码。R2-00（05 §1.3）为分类/品牌补齐
 * {@code CATEGORY_*} / {@code BRAND_*} 共 5 个码，并冻结以下两条口径：
 * </p>
 * <ul>
 *   <li>分类层级超限（三级）与「父级不是一级」<b>不单列错误码</b>，统一
 *       {@code PARAM_INVALID} + 中文 msg（05 §1.2 D 裁定）；</li>
 *   <li>「分类/品牌下仍有商品」的 msg 恒为<b>「请先移走商品」</b>（05 §1.3 逐字）。</li>
 * </ul>
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

    /* ==================== R2 分类（CN-21~24） ==================== */

    /** 同级下分类名重复（409，CN-22/23，唯一键 {@code uk_category_name(pid,name)}） */
    public static final String CATEGORY_NAME_DUPLICATED = "CATEGORY_NAME_DUPLICATED";

    /** 分类下仍有子分类（409，CN-24） */
    public static final String CATEGORY_HAS_CHILDREN = "CATEGORY_HAS_CHILDREN";

    /** 分类（含子分类）下仍有商品，不可删（409，CN-24；msg 恒为「请先移走商品」） */
    public static final String CATEGORY_IN_USE = "CATEGORY_IN_USE";

    /* ==================== R2 品牌（CN-25~28） ==================== */

    /** 品牌名重复（409，CN-26/27，唯一键 {@code uk_brand_name(name)}） */
    public static final String BRAND_NAME_DUPLICATED = "BRAND_NAME_DUPLICATED";

    /** 品牌下仍有商品，不可删（409，CN-28；msg 恒为「请先移走商品」） */
    public static final String BRAND_IN_USE = "BRAND_IN_USE";

    /* ==================== R2 评价（CI-35/CI-36、CN-29~31，05 §2.3.5 / §3.11） ==================== */

    /** 该订单行已评价（409，CI-36 前置校验与唯一键 {@code uk_comment_order_goods} 共用） */
    public static final String COMMENT_ALREADY_EXISTS = "COMMENT_ALREADY_EXISTS";

    /** 评价状态并发冲突（409，CN-31 CAS 落空且当前态≠目标态） */
    public static final String COMMENT_STATUS_CONFLICT = "COMMENT_STATUS_CONFLICT";

    /** 非本人订单（403，CI-36） */
    public static final String NOT_ORDER_OWNER = "NOT_ORDER_OWNER";

    /** 订单未收货，不可评价（400，CI-36） */
    public static final String ORDER_GOODS_NOT_RECEIVED = "ORDER_GOODS_NOT_RECEIVED";

    /** 该订单行已退款，不可评价（400，CI-36） */
    public static final String ORDER_GOODS_REFUNDED = "ORDER_GOODS_REFUNDED";

    private GoodsErrorCode() {
    }
}
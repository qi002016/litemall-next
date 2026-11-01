package com.litemall.support;

import com.litemall.exception.BadRequestException;

/**
 * <p>
 * 商品状态 {@code status}（0 草稿 / 1 在售 / 2 已下架）与布尔 {@code onSale} 的双向映射（08 v4 CI-10/CI-11）。
 * </p>
 * <p>
 * 规则：
 * </p>
 * <ul>
 *   <li>两者都空：新建默认 0（草稿）；编辑表示「不改状态」；</li>
 *   <li>只传 {@code status}：直接采用（须 ∈ {0,1,2}）；</li>
 *   <li>只传 {@code onSale}：true ⇒ 1，false ⇒ 0；</li>
 *   <li>同时传且矛盾（{@code status=1} 但 {@code onSale=false}，或反之）⇒ 400
 *       {@code STATUS_ON_SALE_CONFLICT}。</li>
 * </ul>
 * <p>
 * 判定为纯函数，逐条单测覆盖；admin 不做其它业务判定（03 §6.2）。
 * </p>
 *
 * @since 2026-11-01
 */
public final class GoodsStatusSupport {

    /** 草稿 */
    public static final int DRAFT = 0;
    /** 在售（等价 onSale=true） */
    public static final int ON_SALE = 1;
    /** 已下架 */
    public static final int OFF_SHELF = 2;

    /** status 与 onSale 矛盾（08 v4 CI-10） */
    public static final String CONFLICT_CODE = "STATUS_ON_SALE_CONFLICT";

    private GoodsStatusSupport() {
    }

    /**
     * 新建商品的状态归一：两者都空 ⇒ 0（草稿）。
     *
     * @param status 商品状态
     * @param onSale 是否上架
     * @return 最终写入的状态
     */
    public static Integer resolveForCreate(Integer status, Boolean onSale) {
        return resolve(status, onSale, DRAFT);
    }

    /**
     * 编辑商品的状态归一：两者都空 ⇒ null（不改状态）。
     *
     * @param status 商品状态
     * @param onSale 是否上架
     * @return 最终写入的状态，或 null
     */
    public static Integer resolveForUpdate(Integer status, Boolean onSale) {
        return resolve(status, onSale, null);
    }

    private static Integer resolve(Integer status, Boolean onSale, Integer fallback) {
        if (status != null) {
            if (status < DRAFT || status > OFF_SHELF) {
                throw new BadRequestException("商品状态只能是 0（草稿）/1（在售）/2（已下架）", "PARAM_INVALID");
            }
            if (onSale != null && (status == ON_SALE) != onSale) {
                throw new BadRequestException(
                        "status 与 onSale 矛盾：status=1 表示在售，须 onSale=true；status=0/2 须 onSale=false",
                        CONFLICT_CODE);
            }
            return status;
        }
        if (onSale != null) {
            return onSale ? ON_SALE : DRAFT;
        }
        return fallback;
    }
}
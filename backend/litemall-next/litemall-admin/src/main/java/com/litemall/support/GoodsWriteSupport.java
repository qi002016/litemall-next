package com.litemall.support;

import com.litemall.exception.BadRequestException;

import java.util.List;

/**
 * <p>
 * 商品写接口的跨字段校验（{@code @Valid} 无法表达的条件约束）。
 * </p>
 * <p>
 * 08 v4 CI-10/CI-11：{@code minPrice} 为必填（≥1 分），<b>但提交 {@code skus} 时忽略</b>
 * （最低价由 SKU 派生）。因此这里按「有 SKU ⇒ minPrice 可空；无 SKU ⇒ 必填」判定，
 * 与 goods 服务内的同一语义一致（03 §6.2：admin 只做入参校验，不做价格计算）。
 * </p>
 *
 * @since 2026-11-01
 */
public final class GoodsWriteSupport {

    private GoodsWriteSupport() {
    }

    /**
     * 校验最低售价与 SKU 矩阵的组合合法性。
     *
     * @param minPrice 最低售价（整数分，可空）
     * @param skus     SKU 矩阵（可空）
     */
    public static void validatePrice(Integer minPrice, List<?> skus) {
        boolean hasSkus = skus != null && !skus.isEmpty();
        if (minPrice == null) {
            if (!hasSkus) {
                throw new BadRequestException("未提交 SKU 时最低售价 minPrice 不能为空", "PARAM_INVALID");
            }
            return;
        }
        if (minPrice < 1) {
            throw new BadRequestException("最低售价 minPrice 必须 ≥1 分", "PRICE_ILLEGAL");
        }
    }

    /**
     * 修改（CI-11）时的价格校验：<b>不强制必填</b>，只校验传了的值的下限。
     * <p>
     * 后台列表上下架只发 {@code {id,status}}（{@code GoodsList.vue:166}），而
     * 「无 SKU 时 minPrice 必填」只是新建语义；修改时缺省表示沿用原价，
     * 由 goods 侧 {@code applyBaseFields} 跳过 null 保证。传了且 &lt;1 分仍报
     * 400 {@code PRICE_ILLEGAL}（08 v4 CI-10/11 同一错误码）。
     * </p>
     *
     * @param minPrice 最低售价（整数分，可空）
     */
    public static void validatePriceOnUpdate(Integer minPrice) {
        if (minPrice != null && minPrice < 1) {
            throw new BadRequestException("最低售价 minPrice 必须 ≥1 分", "PRICE_ILLEGAL");
        }
    }
}
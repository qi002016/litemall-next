package com.litemall.domain.DTO;

import com.litemall.exception.GoodsApiException;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * C 端商品列表查询入参（08 v4 CI-31 {@code GET /app/goods/list}）。
 *
 * <p>
 * 归一规则集中在此：页码/页大小夹取、{@code keyword} 空白不过滤且 ≤50 字、
 * {@code minP/maxP} 为整数分且颠倒时自动交换、{@code sort} 非法时<b>忽略落默认</b>（06 §3，不报 400）。
 * </p>
 */
@Data
public class GoodsListQuery {

    private Integer pageNum;

    private Integer pageSize;

    private Long categoryId;

    private Long brandId;

    private String keyword;

    /** 价格下限（整数分） */
    private Integer minP;

    /** 价格上限（整数分） */
    private Integer maxP;

    /** 排序：price_asc | price_desc | new（非法忽略） */
    private String sort;

    /** 只看有货 */
    private Boolean onlyStock;

    /** 默认页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 页大小上限 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 关键字长度上限 */
    public static final int MAX_KEYWORD_LENGTH = 50;

    /**
     * 归一化（就地修改），非法取值抛 {@code PARAM_INVALID}。
     */
    public void normalize() {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                keyword = null;
            } else if (keyword.length() > MAX_KEYWORD_LENGTH) {
                throw GoodsApiException.paramInvalid("搜索关键字最多 " + MAX_KEYWORD_LENGTH + " 字");
            }
        }
        if (minP != null && minP < 0) {
            throw GoodsApiException.paramInvalid("minP 不能为负数");
        }
        if (maxP != null && maxP < 0) {
            throw GoodsApiException.paramInvalid("maxP 不能为负数");
        }
        if (minP != null && maxP != null && minP > maxP) {
            Integer tmp = minP;
            minP = maxP;
            maxP = tmp;
        }
        sort = normalizeSort(sort);
        if (onlyStock == null) {
            onlyStock = Boolean.FALSE;
        }
    }

    /**
     * 排序白名单归一：非法值一律返回 {@code null}（调用方落默认排序）。
     */
    public static String normalizeSort(String sort) {
        if (sort == null) {
            return null;
        }
        String value = sort.trim().toLowerCase();
        List<String> allowed = new ArrayList<>(List.of("price_asc", "price_desc", "new"));
        return allowed.contains(value) ? value : null;
    }
}
package com.litemall.domain.DTO;

import com.litemall.exception.GoodsApiException;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 管理端商品列表查询入参（08 v4 CI-09 / 对内 CI-47）。
 *
 * <p>
 * {@code status} 支持逗号分隔多值（0,1,2），每一项必须是枚举值，否则 400 {@code PARAM_INVALID}；
 * {@code onSale} 等价于 {@code status=1}，两者同时出现时 {@code status} 优先。
 * </p>
 */
@Data
public class GoodsAdminQuery {

    private Integer pageNum;

    private Integer pageSize;

    private String keyword;

    private Long categoryId;

    private Long brandId;

    private String status;

    private Boolean onSale;

    /** addTime | sortOrder | displayPrice | totalStock（非法忽略落默认） */
    private String sortBy;

    public static final int DEFAULT_PAGE_SIZE = 10;

    public static final int MAX_PAGE_SIZE = 100;

    public static final int MAX_KEYWORD_LENGTH = 50;

    private static final List<Integer> ALLOWED_STATUS = List.of(0, 1, 2);

    private static final List<String> ALLOWED_SORT = List.of("addTime", "sortOrder", "displayPrice", "totalStock");

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
        statuses = parseStatuses(status);
        if (statuses.isEmpty() && Boolean.TRUE.equals(onSale)) {
            statuses = List.of(1);
        }
        if (sortBy != null) {
            String value = sortBy.trim();
            sortBy = ALLOWED_SORT.contains(value) ? value : null;
        }
    }

    /** 归一后的状态集合（空 = 不筛） */
    private List<Integer> statuses = new ArrayList<>();

    /**
     * 解析 {@code status} 入参；空 ⇒ 空集合，非法值 ⇒ 400。
     */
    public static List<Integer> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        for (String part : status.split(",")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw GoodsApiException.paramInvalid("status 取值非法：" + status);
            }
            if (!ALLOWED_STATUS.contains(value)) {
                throw GoodsApiException.paramInvalid("status 取值非法：" + status);
            }
            values.add(value);
        }
        return new ArrayList<>(values);
    }
}
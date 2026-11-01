package com.litemall.domain.DTO;

import com.litemall.exception.GoodsApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 库存管理列表查询条件（08 v4 CN-13）。
 * </p>
 *
 * <p>
 * 归一规则与其它查询 DTO 一致：非法枚举值一律<b>静默落默认</b>（不报错），
 * 页码/页长越界一律夹取到合法区间。库存筛选（lowStockOnly/soldOutOnly）依赖
 * order 域派生的 locked，只能在汇总 sellable 之后过滤，故由服务层内存处理。
 * </p>
 */
@Data
@Schema(description = "库存列表查询条件")
public class StockAdminQuery {

    @Schema(description = "页码，默认 1")
    private Integer pageNum;

    @Schema(description = "每页条数，默认 10，最大 100")
    private Integer pageSize;

    @Schema(description = "关键字（商品名称或 SKU 业务码，≤50 字）")
    private String keyword;

    @Schema(description = "类目ID")
    private Long categoryId;

    @Schema(description = "商品状态：0 草稿 / 1 在售 / 2 已下架（逗号分隔可多值）")
    private String goodsStatus;

    @Schema(description = "仅看低库存（sellable ≤ 5）")
    private Boolean lowStockOnly;

    @Schema(description = "仅看已售罄（sellable = 0）")
    private Boolean soldOutOnly;

    @Schema(description = "排序字段：sellable（默认，升序）/ number / updateTime")
    private String sortBy;

    /** 归一后的页码 */
    public int pageNumOrDefault() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /** 归一后的页长 */
    public int pageSizeOrDefault() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    /** 归一后的关键字：空白 ⇒ null */
    public String keywordOrNull() {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 归一后的排序字段：白名单外一律默认 sellable */
    public String sortByOrDefault() {
        if ("number".equals(sortBy) || "updateTime".equals(sortBy)) {
            return sortBy;
        }
        return "sellable";
    }

    /**
     * 解析状态多值（与 CI-47 同规则：逗号分隔，只允许 0/1/2）。
     *
     * @return 状态列表；未传 ⇒ null
     */
    public java.util.List<Integer> parseStatuses() {
        String raw = goodsStatus == null ? null : goodsStatus.trim();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        java.util.List<Integer> statuses = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            int status;
            try {
                status = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw GoodsApiException.paramInvalid("goodsStatus 取值非法：" + value);
            }
            if (status < 0 || status > 2) {
                throw GoodsApiException.paramInvalid("goodsStatus 取值非法：" + value);
            }
            if (!statuses.contains(status)) {
                statuses.add(status);
            }
        }
        return statuses.isEmpty() ? null : statuses;
    }
}
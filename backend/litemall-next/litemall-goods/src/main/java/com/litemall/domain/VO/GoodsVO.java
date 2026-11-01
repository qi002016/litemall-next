package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品列表行（08 v4 CI-31 {@code GET /app/goods/list}）。
 *
 * <p>
 * 金额一律<b>整数分</b>；ID 由全局 Jackson 配置序列化为字符串。
 * </p>
 *
 * <p>
 * 三个价格口径必须分清：
 * </p>
 * <ul>
 *   <li>{@code minPrice}：库内全部 SKU 的最低价（含无货 SKU），用于「划线价」类展示；</li>
 *   <li>{@code displayPrice}：<b>有可售</b> SKU 的最低价；无任何可售 SKU 时回落到 {@code minPrice} 且 {@code soldOut=true}；</li>
 *   <li>{@code totalStock}：Σ sellable（可售量），不是 Σ number。</li>
 * </ul>
 */
@Data
@Schema(description = "商品列表行")
public class GoodsVO {

    @Schema(description = "商品ID", type = "string")
    private Long id;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "简介")
    private String brief;

    @Schema(description = "主图")
    private String picUrl;

    @Schema(description = "最低售价（整数分，含无货 SKU）", type = "integer")
    private Integer minPrice;

    @Schema(description = "展示价（整数分，有可售 SKU 的最低价；无可售时等于 minPrice）", type = "integer")
    private Integer displayPrice;

    @Schema(description = "总可售量（Σ sellable；locked 不可用时为 null）", type = "integer")
    private Integer totalStock;

    @Schema(description = "是否售罄（totalStock == 0）")
    private Boolean soldOut;

    @Schema(description = "类目ID", type = "string")
    private Long categoryId;

    @Schema(description = "品牌ID", type = "string")
    private Long brandId;

    @Schema(description = "锁定库存是否不可用（order 域调用失败；true 时 totalStock/displayPrice 为降级值）")
    private Boolean lockedUnavailable;
}
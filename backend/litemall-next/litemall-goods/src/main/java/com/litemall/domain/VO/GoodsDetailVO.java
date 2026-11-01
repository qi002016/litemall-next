package com.litemall.domain.VO;

import com.litemall.domain.PO.GoodsSpecification;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 商品详情（08 v4 CI-32 {@code GET /app/goods/detail}）。
 *
 * <p>
 * 在旧结构上增量扩展，旧字段（{@code price/stock/specList/commentCount}）语义保留：
 * </p>
 * <ul>
 *   <li>{@code price}：旧口径「最低售价」，R1 起等于 {@code displayPrice}（整数分）；</li>
 *   <li>{@code stock}：Σ number（实际库存，**语义不变**，不是可售）；</li>
 *   <li>{@code availableStock}：Σ sellable（可售，= Σ max(0, number − locked)）；</li>
 *   <li>{@code canBuy}：至少一个 SKU 可售 &gt; 0；</li>
 *   <li>{@code commentCount}：只计 {@code status=0} 的评论。</li>
 * </ul>
 */
@Data
@Schema(description = "商品详情VO")
public class GoodsDetailVO {

    @Schema(description = "商品ID", type = "string")
    private Long id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "简介")
    private String brief;

    @Schema(description = "主图")
    private String picUrl;

    @Schema(description = "轮播图")
    private String[] gallery;

    @Schema(description = "详情（富文本）")
    private String detail;

    @Schema(description = "展示价（整数分，= displayPrice 口径）", type = "integer")
    private Integer price;

    @Schema(description = "实际库存 Σ number（语义不变）", type = "integer")
    private Integer stock;

    @Schema(description = "规格列表（旧结构，保留兼容）")
    private List<GoodsSpecification> specList;

    @Schema(description = "评论数（只计 status=0）", type = "integer")
    private Integer commentCount;

    /* ==================== R1 新增 ==================== */

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "专柜价（整数分）", type = "integer")
    private Integer counterPrice;

    @Schema(description = "类目ID", type = "string")
    private Long categoryId;

    @Schema(description = "品牌ID", type = "string")
    private Long brandId;

    @Schema(description = "可售总量（Σ sellable；locked 不可用时为 null）", type = "integer")
    private Integer availableStock;

    @Schema(description = "是否可购买（至少一个 SKU 可售 > 0）")
    private Boolean canBuy;

    @Schema(description = "规格维度（含可选值，含图片）")
    private List<SpecVO> specs;

    @Schema(description = "SKU 列表（价与可售量按规格返回）")
    private List<SkuVO> skus;

    @Schema(description = "商品参数")
    private List<AttributeVO> attributes;

    @Schema(description = "锁定库存是否不可用（order 域调用失败；true 时 availableStock/各 SKU sellable 为 null）")
    private Boolean lockedUnavailable;

    /** 规格维度 */
    @Data
    @Schema(description = "规格维度")
    public static class SpecVO {
        @Schema(description = "维度名称，如「颜色」")
        private String name;

        @Schema(description = "维度取值")
        private List<SpecValueVO> values;
    }

    /** 规格取值 */
    @Data
    @Schema(description = "规格取值")
    public static class SpecValueVO {
        @Schema(description = "取值ID", type = "string")
        private String valueId;

        @Schema(description = "取值文本，如「白色」")
        private String value;

        @Schema(description = "取值图片")
        private String picUrl;
    }

    /** SKU */
    @Data
    @Schema(description = "SKU")
    public static class SkuVO {
        @Schema(description = "货品ID", type = "string")
        private Long id;

        @Schema(description = "SKU 业务码，如 215-3_7")
        private String skuCode;

        @Schema(description = "规格值ID（升序）")
        private List<String> specValueIds;

        @Schema(description = "规格文本，如「白色 / 350ml」")
        private String specText;

        @Schema(description = "售价（整数分）", type = "integer")
        private Integer price;

        @Schema(description = "实际库存", type = "integer")
        private Integer number;

        @Schema(description = "可售数量（number − locked；locked 不可用时为 null）", type = "integer")
        private Integer sellable;

        @Schema(description = "货品图")
        private String url;
    }

    /** 商品参数 */
    @Data
    @Schema(description = "商品参数")
    public static class AttributeVO {
        @Schema(description = "参数名")
        private String name;

        @Schema(description = "参数值")
        private String value;
    }
}
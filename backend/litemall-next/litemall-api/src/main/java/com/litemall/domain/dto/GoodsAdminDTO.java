package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 管理端商品行 / 商品写操作 DTO（H5：admin 不再直写 litemall_goods）
 * </p>
 *
 * <p>
 * R1（08 v4 CI-09/CI-10/CI-11）重写：
 * </p>
 * <ul>
 *   <li><b>金额一律整数分</b>：{@code minPrice} / {@code displayPrice} / {@code counterPrice} /
 *       {@code skus[].price} 均为「分」，库内 DECIMAL 元在服务边界转换。</li>
 *   <li>列表行新增 {@code status/statusText/displayPrice/totalStock/skuCount/categoryName/brandName/addTime/updateTime}。</li>
 *   <li>写操作新增 {@code categoryId/brandId/detail/gallery/unit/counterPrice/keywords/sortOrder}
 *       以及规格维度 {@code specifications} 与 SKU 矩阵 {@code skus}（编辑时传了即全量覆盖）。</li>
 *   <li>{@code status}(0 草稿 / 1 在售 / 2 已下架) 与 {@code onSale} 双向映射：两者同时传且矛盾 ⇒ 400
 *       {@code STATUS_ON_SALE_CONFLICT}。</li>
 * </ul>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端商品信息")
public class GoodsAdminDTO {

    /* ==================== 标识与基础信息 ==================== */

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "商品名称（1–120 字）")
    private String name;

    @Schema(description = "商品简介（≤255 字）")
    private String brief;

    @Schema(description = "商品主图")
    private String picUrl;

    @Schema(description = "商品详情（富文本，≤20000 字）")
    private String detail;

    @Schema(description = "商品图集（≤8 张，缺省取 [picUrl]）")
    private List<String> gallery;

    @Schema(description = "商品单位，缺省「件」")
    private String unit;

    @Schema(description = "关键字，逗号分隔")
    private String keywords;

    @Schema(description = "排序权重，缺省 100")
    private Integer sortOrder;

    @Schema(description = "所属类目ID（新建必填，须末级）")
    private Long categoryId;

    @Schema(description = "品牌ID")
    private Long brandId;

    /* ==================== 金额（整数分） ==================== */

    @Schema(description = "最低售价（整数分；传 skus 时忽略，由 SKU 最低价派生）", type = "integer")
    private Integer minPrice;

    @Schema(description = "专柜价（整数分）", type = "integer")
    private Integer counterPrice;

    @Schema(description = "展示价：有可售 SKU 的最低售价；无可售时等于 minPrice（整数分，只读）", type = "integer")
    private Integer displayPrice;

    /* ==================== 状态与库存（列表行） ==================== */

    @Schema(description = "是否上架（与 status 双向映射）")
    private Boolean onSale;

    @Schema(description = "商品状态：0 草稿 / 1 在售 / 2 已下架", type = "integer")
    private Integer status;

    @Schema(description = "商品状态文案（只读）")
    private String statusText;

    @Schema(description = "总可售量（Σ SKU sellable，只读）", type = "integer")
    private Integer totalStock;

    @Schema(description = "SKU 数量（只读）", type = "integer")
    private Integer skuCount;

    /* ==================== 展示辅助（列表行，只读） ==================== */

    @Schema(description = "类目名称（只读）")
    private String categoryName;

    @Schema(description = "品牌名称（只读）")
    private String brandName;

    @Schema(description = "创建时间 yyyy-MM-dd HH:mm:ss（只读）")
    private String addTime;

    /** 更新时间 yyyy-MM-dd HH:mm:ss；编辑时可回传，用于并发校验（不一致 ⇒ 409 CONCURRENT_MODIFIED）。 */
    @Schema(description = "更新时间 yyyy-MM-dd HH:mm:ss（编辑可回传做并发校验）")
    private String updateTime;

    /* ==================== 规格维度与 SKU 矩阵 ==================== */

    @Schema(description = "规格维度（1–2 维；传了即全量重建；缺省=默认维度）")
    private List<SpecDTO> specifications;

    @Schema(description = "SKU 矩阵（≥1；传了即全量覆盖：带 id=改、不带=建、未出现=删）")
    private List<SkuDTO> skus;

    /** 规格维度 */
    @Data
    public static class SpecDTO {
        @Schema(description = "维度名称，如「颜色」（1–32 字，商品内不重名）")
        private String name;

        @Schema(description = "维度取值，如 [\"白色\",\"黑色\"]（同维不重复）")
        private List<String> values;
    }

    /** SKU（货品） */
    @Data
    public static class SkuDTO {
        @Schema(description = "货品ID：编辑时带=改，不带=新建")
        private Long id;

        @Schema(description = "规格值ID列表（升序；新建可传临时序号/值文本，服务端翻译为真ID）")
        private List<String> specValueIds;

        @Schema(description = "售价（整数分，≥1）", type = "integer")
        private Integer price;

        @Schema(description = "库存数量（≥0）", type = "integer")
        private Integer number;

        @Schema(description = "货品图")
        private String url;

        @Schema(description = "SKU 业务码（≤64，全表唯一；缺省按 goodsId-specValueId 升序自动生成）")
        private String skuCode;
    }
}
package com.litemall.domain.DTO;

import com.litemall.domain.dto.GoodsAdminDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理端修改商品请求（08 v4 CI-11 {@code PUT /admin/goods/update}）。
 * <p>
 * 与新建的差异：{@code id} 必填；{@code updateTime} 可选，回传即做乐观并发校验
 * （不一致 ⇒ 409 {@code CONCURRENT_MODIFIED}，在 goods 侧判定）；
 * {@code skus} 传了即全量覆盖（带 id=改、不带=建、未出现=删）。
 * </p>
 * <p>
 * 与新建拆成两个类的原因：{@code id} 只在修改时必需，{@code updateTime} 只在修改时可选回传。
 * </p>
 * <p>
 * <b>部分更新语义</b>（R1-05 实测对齐）：后台列表的上下架只发 {@code {id,status}}
 * （{@code frontend/admin-web/src/views/goods/GoodsList.vue:166}），goods 侧
 * {@code GoodsWriteServiceImpl.applyBaseFields} 对 null 字段跳过、{@code resolveStatus}
 * 未传时沿用原状态，因此这里除 {@code id} 外全部改为可选；{@code minPrice} 的
 * 「无 SKU 时必填」只在新建（CI-10）成立，修改时不强制，仅在传了且 &lt;1 分时由
 * {@code GoodsWriteSupport} 报 400 {@code PRICE_ILLEGAL}。
 * </p>
 */
@Data
@Schema(description = "管理端修改商品请求")
public class GoodsUpdateDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long id;

    @Size(max = 120, message = "商品名称长度不能超过 120 位")
    @Schema(description = "商品名称（1–120 字；部分更新时可不传）")
    private String name;

    @Size(max = 255, message = "商品简介长度不能超过 255 位")
    @Schema(description = "商品简介（≤255 字）")
    private String brief;

    @Size(max = 255, message = "商品主图地址长度不能超过 255 位")
    @Schema(description = "商品主图（≤255；部分更新时可不传）")
    private String picUrl;

    @Size(max = 20000, message = "商品详情长度不能超过 20000 位")
    @Schema(description = "商品详情（≤20000 字）")
    private String detail;

    @Size(max = 8, message = "商品图集最多 8 张")
    @Schema(description = "商品图集（≤8 张）")
    private List<String> gallery;

    @Size(max = 16, message = "商品单位长度不能超过 16 位")
    @Schema(description = "商品单位")
    private String unit;

    @Size(max = 255, message = "关键字长度不能超过 255 位")
    @Schema(description = "关键字，逗号分隔")
    private String keywords;

    @Min(value = 0, message = "排序权重不能为负")
    @Schema(description = "排序权重")
    private Integer sortOrder;

    @Schema(description = "所属类目ID（须末级；部分更新时可不传）")
    private Long categoryId;

    @Schema(description = "品牌ID")
    private Long brandId;

    @Schema(description = "最低售价（整数分；提交 skus 时忽略，部分更新时可不传）", type = "integer")
    private Integer minPrice;

    @Min(value = 0, message = "专柜价不能为负")
    @Schema(description = "专柜价（整数分）", type = "integer")
    private Integer counterPrice;

    @Schema(description = "是否上架（与 status 双向映射）")
    private Boolean onSale;

    @Schema(description = "商品状态：0 草稿 / 1 在售 / 2 已下架")
    private Integer status;

    @Valid
    @Size(min = 1, max = 2, message = "规格维度为 1–2 维")
    @Schema(description = "规格维度（1–2 维；传了即全量重建）")
    private List<GoodsAdminDTO.SpecDTO> specifications;

    @Valid
    @Size(max = 200, message = "SKU 数量不能超过 200")
    @Schema(description = "SKU 矩阵（传了即全量覆盖）")
    private List<GoodsAdminDTO.SkuDTO> skus;

    @Schema(description = "更新时间 yyyy-MM-dd HH:mm:ss（可选，回传做并发校验）")
    private String updateTime;
}
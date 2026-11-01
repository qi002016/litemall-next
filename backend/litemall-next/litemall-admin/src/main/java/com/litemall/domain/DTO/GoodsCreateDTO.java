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
 * 管理端新建商品请求（08 v4 CI-10 {@code POST /admin/goods/create}）。
 * <p>
 * 与改造前相比：金额改为整数分（{@code minPrice}/{@code counterPrice}/SKU 价格），新增
 * 类目、品牌、详情、图集、规格维度、SKU 矩阵与状态字段；{@code status} 与 {@code onSale}
 * 的映射与矛盾校验见 {@code GoodsStatusSupport}。
 * </p>
 * <p>
 * {@code minPrice} 的「必填但当提交 SKU 时忽略」由 {@code GoodsWriteSupport} 兜底
 * （{@code @Valid} 无法表达条件必填）。
 * </p>
 */
@Data
@Schema(description = "管理端新建商品请求")
public class GoodsCreateDTO {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 120, message = "商品名称长度不能超过 120 位")
    @Schema(description = "商品名称（1–120 字）")
    private String name;

    @Size(max = 255, message = "商品简介长度不能超过 255 位")
    @Schema(description = "商品简介（≤255 字）")
    private String brief;

    @NotBlank(message = "商品主图不能为空")
    @Size(max = 255, message = "商品主图地址长度不能超过 255 位")
    @Schema(description = "商品主图（必填，≤255）")
    private String picUrl;

    @Size(max = 20000, message = "商品详情长度不能超过 20000 位")
    @Schema(description = "商品详情（≤20000 字）")
    private String detail;

    @Size(max = 8, message = "商品图集最多 8 张")
    @Schema(description = "商品图集（≤8 张，缺省取 [picUrl]）")
    private List<String> gallery;

    @Size(max = 16, message = "商品单位长度不能超过 16 位")
    @Schema(description = "商品单位，缺省「件」")
    private String unit;

    @Size(max = 255, message = "关键字长度不能超过 255 位")
    @Schema(description = "关键字，逗号分隔")
    private String keywords;

    @Min(value = 0, message = "排序权重不能为负")
    @Schema(description = "排序权重，缺省 100")
    private Integer sortOrder;

    @NotNull(message = "类目不能为空")
    @Schema(description = "所属类目ID（必填，须末级）")
    private Long categoryId;

    @Schema(description = "品牌ID")
    private Long brandId;

    @Min(value = 1, message = "最低售价必须 ≥1 分")
    @Schema(description = "最低售价（整数分；提交 skus 时忽略）", type = "integer")
    private Integer minPrice;

    @Min(value = 0, message = "专柜价不能为负")
    @Schema(description = "专柜价（整数分）", type = "integer")
    private Integer counterPrice;

    @Schema(description = "是否上架（true ⇔ status=1，false ⇔ status=0；缺省 false）")
    private Boolean onSale;

    @Schema(description = "商品状态：0 草稿 / 1 在售 / 2 已下架（缺省 0）", type = "integer")
    private Integer status;

    @Valid
    @Size(min = 1, max = 2, message = "规格维度为 1–2 维")
    @Schema(description = "规格维度（1–2 维）")
    private List<GoodsAdminDTO.SpecDTO> specifications;

    @Valid
    @Size(max = 200, message = "SKU 数量不能超过 200")
    @Schema(description = "SKU 矩阵（≥1；新建时规格值可传文本，由服务端翻译为真 ID）")
    private List<GoodsAdminDTO.SkuDTO> skus;
}
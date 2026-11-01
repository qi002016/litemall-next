package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端新建品牌请求（08 v4 CN-26 {@code POST /admin/brand/create}）。
 * <p>
 * 金额口径（05 §1.5）：{@code floorPrice} 一律<b>整数分</b>，落库时在 goods 侧换算成
 * {@code DECIMAL(10,2)} 元。品牌名全局唯一（{@code uk_brand_name}），重名由 goods 侧转 409。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端新建品牌请求")
public class BrandCreateDTO {

    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 64, message = "品牌名称长度不能超过 64 位")
    @Schema(description = "品牌名称（1–64 字，全局唯一）")
    private String name;

    @Size(max = 255, message = "品牌描述长度不能超过 255 位")
    @Schema(description = "品牌描述（≤255）")
    private String desc;

    @Size(max = 255, message = "品牌图片地址长度不能超过 255 位")
    @Schema(description = "品牌图片（≤255）")
    private String picUrl;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值（≥0，默认 0）")
    private Integer sortOrder;

    @Min(value = 0, message = "品牌商品最低价不能小于 0")
    @Schema(description = "品牌商品最低价（整数分，≥0，默认 0）")
    private Long floorPrice;
}
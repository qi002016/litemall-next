package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端修改品牌请求（08 v4 CN-27 {@code PUT /admin/brand/update}）。
 * <p>
 * 部分更新：{@code null} 即「不修改」。{@code floorPrice} 是整数分；
 * {@code updateTime} 回传则做 CAS，不等 ⇒ 409 {@code CONCURRENT_MODIFIED}。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端修改品牌请求")
public class BrandUpdateDTO {

    @NotNull(message = "品牌ID不能为空")
    @Schema(description = "品牌ID")
    private Long id;

    @Size(max = 64, message = "品牌名称长度不能超过 64 位")
    @Schema(description = "品牌名称（≤64，全局唯一）")
    private String name;

    @Size(max = 255, message = "品牌描述长度不能超过 255 位")
    @Schema(description = "品牌描述（≤255）")
    private String desc;

    @Size(max = 255, message = "品牌图片地址长度不能超过 255 位")
    @Schema(description = "品牌图片（≤255）")
    private String picUrl;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值（≥0）")
    private Integer sortOrder;

    @Min(value = 0, message = "品牌商品最低价不能小于 0")
    @Schema(description = "品牌商品最低价（整数分，≥0）")
    private Long floorPrice;

    @Schema(description = "并发控制用更新时间（yyyy-MM-dd HH:mm:ss，可空）")
    private String updateTime;
}
package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端品牌新建/修改入参（08 v4 CN-26 / CN-27，对内 {@code /inner/goods/admin/brand/*}）。
 * </p>
 * <p>
 * 修改为部分更新（null 保持原值）；{@code id} 仅修改时必填。
 * {@code floorPrice} 为<b>整数分</b>，goods 侧落库时转成 {@code DECIMAL(10,2)} 元。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端品牌新建/修改入参")
public class BrandAdminDTO {

    @Schema(description = "品牌ID（修改时必填）", type = "string")
    private Long id;

    @Schema(description = "品牌名称（1–64 字，全局唯一）")
    private String name;

    @Schema(description = "品牌简介（≤255）")
    private String desc;

    @Schema(description = "品牌图片（≤255）")
    private String picUrl;

    @Schema(description = "排序值（≥0，默认 0）")
    private Integer sortOrder;

    @Schema(description = "品牌商品最低价（整数分，≥0，默认 0）", type = "integer")
    private Long floorPrice;

    @Schema(description = "并发校验回传的更新时间 yyyy-MM-dd HH:mm:ss")
    private String updateTime;
}
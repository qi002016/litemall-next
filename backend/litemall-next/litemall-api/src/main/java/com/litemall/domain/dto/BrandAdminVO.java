package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端品牌行（08 v4 CN-25 出参 / 对内 {@code /inner/goods/admin/brand/list}）。
 * </p>
 * <ul>
 *   <li>{@code floorPrice}：<b>整数分</b>（库内 {@code DECIMAL(10,2)} 元 → ×100 后取整），
 *       与 08 全局「金额一律整数分」口径一致；</li>
 *   <li>{@code goodsCount}：字符串形式计数（含已下架商品，不含已删除商品）；</li>
 *   <li>{@code addTime/updateTime}：{@code yyyy-MM-dd HH:mm:ss}。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端品牌行")
public class BrandAdminVO {

    @Schema(description = "品牌ID", type = "string")
    private Long id;

    @Schema(description = "品牌名称")
    private String name;

    @Schema(description = "品牌简介")
    private String desc;

    @Schema(description = "品牌图片")
    private String picUrl;

    @Schema(description = "排序值，越小越靠前")
    private Integer sortOrder;

    @Schema(description = "品牌商品最低价（整数分）", type = "integer")
    private Long floorPrice;

    @Schema(description = "该品牌下商品数（含已下架，不含已删除）", type = "string")
    private String goodsCount;

    @Schema(description = "创建时间 yyyy-MM-dd HH:mm:ss")
    private String addTime;

    @Schema(description = "更新时间 yyyy-MM-dd HH:mm:ss")
    private String updateTime;
}
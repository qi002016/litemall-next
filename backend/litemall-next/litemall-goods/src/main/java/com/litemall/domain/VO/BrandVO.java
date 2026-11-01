package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 品牌商 VO
 * </p>
 *
 * <p>
 * 金额口径（05 §1.5 / 08 全局口径）：库内 {@code DECIMAL(10,2)} 元，
 * <b>出参一律整数分</b>，故 {@code floorPrice} 为 {@code Long}（分），
 * 由 {@code BrandServiceImpl#getBrandList} 用 {@code CatalogSupport.toCents} 换算。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
@Schema(description = "品牌商VO")
public class BrandVO {

    @Schema(description = "品牌ID", type = "string")
    private Long id;

    @Schema(description = "品牌名称")
    private String name;

    @Schema(description = "品牌简介")
    private String desc;

    @Schema(description = "品牌图片")
    private String picUrl;

    @Schema(description = "品牌商品最低价（整数分，库内元 ×100 取整）", type = "integer")
    private Long floorPrice;
}
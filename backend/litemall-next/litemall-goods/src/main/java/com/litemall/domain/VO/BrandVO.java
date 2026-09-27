package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * <p>
 * 品牌商 VO
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

    @Schema(description = "品牌商品最低价, 仅用于页面展示")
    private BigDecimal floorPrice;
}

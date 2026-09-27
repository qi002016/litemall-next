package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 商品分类树 VO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
@Schema(description = "商品分类树节点")
public class CategoryVO {

    @Schema(description = "分类ID", type = "string")
    private Long id;

    @Schema(description = "分类名")
    private String name;

    @Schema(description = "图标")
    private String picUrl;

    @Schema(description = "子分类")
    private List<CategoryVO> children;
}

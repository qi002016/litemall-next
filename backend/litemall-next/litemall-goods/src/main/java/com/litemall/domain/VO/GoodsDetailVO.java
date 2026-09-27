package com.litemall.domain.VO;

import com.litemall.domain.PO.GoodsSpecification;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "商品详情VO")
public class GoodsDetailVO {
    @Schema(description = "商品ID", type = "string")
    private Long id;
    @Schema(description = "名称")
    private String name;
    @Schema(description = "简介")
    private String brief;
    @Schema(description = "主图")
    private String picUrl;
    @Schema(description = "轮播图")
    private String[] gallery;
    @Schema(description = "详情 (富文本)")
    private String detail;
    @Schema(description = "价格")
    private BigDecimal price;
    @Schema(description = "库存")
    private Integer stock;
    @Schema(description = "规格列表")
    private List<GoodsSpecification> specList;
    @Schema(description = "评论数")
    private Integer commentCount;
}

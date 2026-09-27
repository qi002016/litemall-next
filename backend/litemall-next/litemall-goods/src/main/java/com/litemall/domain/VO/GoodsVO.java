package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "商品VO")
public class GoodsVO {

    @Schema(description = "商品ID", type = "string")
    private Long id;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "简介")
    private String brief;

    @Schema(description = "主图")
    private String picUrl;

    @Schema(description = "最低价格")
    private BigDecimal minPrice;
}

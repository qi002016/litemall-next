package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "购物车项VO")
public class CartItemVO {

    @Schema(description = "购物车项ID", type = "string")
    private Long id;

    @Schema(description = "商品ID", type = "string")
    private Long goodsId;

    @Schema(description = "商品名")
    private String goodsName;

    @Schema(description = "商品图")
    private String goodsPic;

    @Schema(description = "货品/规格ID", type = "string")
    private Long specId;

    @Schema(description = "单价")
    private BigDecimal price;

    @Schema(description = "数量")
    private Integer number;

    @Schema(description = "规格")
    private String spec;

    @Schema(description = "是否选中")
    private Boolean checked;
}

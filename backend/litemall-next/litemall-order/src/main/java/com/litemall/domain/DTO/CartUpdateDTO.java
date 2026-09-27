package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改购物车DTO")
public class CartUpdateDTO {

    @Schema(description = "商品ID")
    private Long goodsId;

    @Schema(description = "规格/货品ID")
    private Long specId;

    @Schema(description = "数量")
    private Integer number;

    @Schema(description = "是否选中")
    private Boolean checked;
}

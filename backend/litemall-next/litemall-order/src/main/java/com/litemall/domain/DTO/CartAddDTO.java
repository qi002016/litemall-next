package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "加入购物车DTO")
public class CartAddDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long goodsId;

    @NotNull(message = "规格ID不能为空")
    @Schema(description = "规格/货品ID")
    private Long specId;

    @NotNull(message = "数量不能为空")
    @Schema(description = "数量")
    private Integer number;
}

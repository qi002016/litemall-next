package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "收藏/取消收藏DTO")
public class CollectAddDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long goodsId;

    @NotNull(message = "类型不能为空")
    @Min(value = 0, message = "type 仅支持 0(收藏) / 1(取消)")
    @Max(value = 1, message = "type 仅支持 0(收藏) / 1(取消)")
    @Schema(description = "0 = 收藏，1 = 取消")
    private Integer type;
}

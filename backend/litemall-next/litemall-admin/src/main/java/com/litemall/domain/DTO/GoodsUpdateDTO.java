package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * M13：管理端修改商品请求（原为 {@code Map<String, Object>}）。
 * <p>
 * 与新建拆成两个类的原因：{@code id} 只在修改时必需，单个 DTO 无法同时表达
 * "新建可不带 id / 修改必须带 id"。
 */
@Data
@Schema(description = "管理端修改商品请求")
public class GoodsUpdateDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long id;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 120, message = "商品名称长度不能超过 120 位")
    @Schema(description = "商品名称")
    private String name;

    @Size(max = 255, message = "商品简介长度不能超过 255 位")
    @Schema(description = "商品简介")
    private String brief;

    @Size(max = 255, message = "图片地址长度不能超过 255 位")
    @Schema(description = "商品图片")
    private String picUrl;

    @DecimalMin(value = "0", message = "零售价不能为负")
    @Schema(description = "零售价")
    private BigDecimal minPrice;

    @Schema(description = "是否上架")
    private Boolean onSale;
}
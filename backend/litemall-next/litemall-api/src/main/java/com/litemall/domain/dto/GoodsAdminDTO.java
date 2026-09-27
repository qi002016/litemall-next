package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * <p>
 * 管理端商品行 / 商品写操作 DTO（H5：admin 不再直写 litemall_goods）
 * </p>
 * <p>
 * 字段与改造前 {@code admin/controller/GoodsController#list} 返回的 Map 及
 * create/update 请求体保持一一对应，管理端前端契约不变。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端商品信息")
public class GoodsAdminDTO {

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "商品简介")
    private String brief;

    @Schema(description = "商品图片")
    private String picUrl;

    /** 前端列名 minPrice，落到 litemall_goods.retail_price */
    @Schema(description = "零售价")
    private BigDecimal minPrice;

    @Schema(description = "是否上架")
    private Boolean onSale;
}
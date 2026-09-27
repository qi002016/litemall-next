package com.litemall.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * <p>
 * 商品货品信息 DTO（Feign 跨服务调用）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
@Data
public class GoodsProductInfoDTO {

    /** 货品ID */
    private Long id;

    /** 商品ID */
    private Long goodsId;

    /** 商品名 */
    private String goodsName;

    /** 商品编号 */
    private String goodsSn;

    /** 商品封面图 */
    private String picUrl;

    /** 货品价格 */
    private BigDecimal price;

    /** 规格值数组，JSON */
    private String specifications;

    /** 货品图 */
    private String url;
}

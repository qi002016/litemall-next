package com.litemall.domain.dto;

import lombok.Data;

/**
 * <p>
 * 商品简要信息 DTO（Feign 跨服务调用：收藏/足迹列表展示用）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
public class GoodsBriefDTO {

    /** 商品ID */
    private Long id;

    /** 商品名 */
    private String name;

    /** 商品主图 */
    private String picUrl;

    /**
     * 售价（整数分）。收藏列表要展示价格，故只增此字段、不动既有字段，
     * 避免足迹等其它调用方被迫同批发布；取可售 SKU 最低价，无 SKU 回落 retail_price×100，查不到留 null。
     */
    private Integer price;

    /** 是否上架（is_on_sale=1 且未删除）。收藏列表要标「已下架」；商品不存在时由调用方给 false。 */
    private Boolean onSale;
}

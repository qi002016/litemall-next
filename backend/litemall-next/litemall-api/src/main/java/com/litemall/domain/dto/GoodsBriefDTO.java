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
}

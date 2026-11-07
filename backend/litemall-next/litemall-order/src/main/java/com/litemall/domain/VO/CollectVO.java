package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 收藏VO（4.12）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "收藏VO")
public class CollectVO {

    @Schema(description = "收藏记录ID", type = "string")
    private Long id;

    @Schema(description = "商品ID", type = "string")
    private Long goodsId;

    @Schema(description = "商品名")
    private String goodsName;

    @Schema(description = "商品主图")
    private String picUrl;

    @Schema(description = "售价（整数分）；商品不存在时为 null")
    private Integer price;

    @Schema(description = "是否上架；商品不存在/已下架为 false")
    private Boolean onSale;

    @Schema(description = "收藏时间（yyyy-MM-dd HH:mm:ss）")
    private String addTime;
}

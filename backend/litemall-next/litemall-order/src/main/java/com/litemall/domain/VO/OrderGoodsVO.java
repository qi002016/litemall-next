package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 订单商品VO（4.7 列表 goodsList / 4.8 详情 goodsList 共用基础字段）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "订单商品VO")
public class OrderGoodsVO {

    @Schema(description = "商品ID", type = "string")
    private Long goodsId;

    @Schema(description = "商品名")
    private String goodsName;

    @Schema(description = "商品图")
    private String picUrl;

    @Schema(description = "数量")
    private Integer number;

    @Schema(description = "单价")
    private BigDecimal price;

    /* ===== 4.8 详情额外字段 ===== */

    @Schema(description = "规格项ID（即 productId）", type = "string")
    private Long productId;

    @Schema(description = "规格描述数组")
    private List<String> specifications;

    @Schema(description = "评论状态：-1 不可评 / 0 可评 / >0 评论ID")
    private Integer commentStatus;
}

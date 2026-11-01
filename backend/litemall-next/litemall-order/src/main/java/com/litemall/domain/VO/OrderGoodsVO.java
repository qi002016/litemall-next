package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 订单商品VO（CI-83 列表 {@code goodsList} / CI-84 详情 {@code goodsList} 共用基础字段）
 * </p>
 * <p>
 * R1：新增 {@code orderGoodsId/specText/refundStatus/refundStatusText}；
 * {@code price} 改 <b>int 分</b>（06 §4）；{@code commentStatus}：-1 不可评 / 0 可评 / &gt;0 评论ID。
 * </p>
 */
@Data
@Schema(description = "订单商品VO")
public class OrderGoodsVO {

    @Schema(description = "订单行ID", type = "string")
    private Long orderGoodsId;

    @Schema(description = "商品ID", type = "string")
    private Long goodsId;

    @Schema(description = "商品名")
    private String goodsName;

    @Schema(description = "商品图")
    private String picUrl;

    @Schema(description = "数量")
    private Integer number;

    @Schema(description = "单价（分）")
    private Integer price;

    @Schema(description = "规格文案，如「黑色 / L」")
    private String specText;

    @Schema(description = "行退款状态 0 未退款 / 1 部分退款 / 2 全额退款")
    private Integer refundStatus;

    @Schema(description = "行退款状态文案")
    private String refundStatusText;

    /* ===== 详情额外字段 ===== */

    @Schema(description = "规格项ID（即 productId）", type = "string")
    private Long productId;

    @Schema(description = "规格描述数组")
    private List<String> specifications;

    @Schema(description = "评论状态：-1 不可评 / 0 可评 / >0 评论ID")
    private Integer commentStatus;
}
package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * CN-IN-01 出参：供评价服务校验订单行（{@code GET /inner/order/admin/order-goods/{id}}，07 §6.1）。
 * <p>
 * 只读接口。R2 口径（R2-00 §4.5）：{@code canComment = orderStatus == 301 且 refundStatus≠2 且 !commented}
 * （删除 R1 的 401 分支，BR-CM-01「只有已收货可评」）；出参只增 {@code goodsId}（CI-36 用它填
 * {@code value_id}，<b>不采信</b>前端传的 goodsId）。{@code canComment} 仅供前端置灰，
 * CI-36 不得以它作为放行依据。
 * </p>
 * <p>
 * 不返回 403（userId 传入且不匹配时按 404，避免泄露他人订单行的存在性）。
 * </p>
 */
@Data
@Schema(description = "订单行评价校验信息")
public class OrderGoodsStatusDTO {

    @Schema(description = "订单行ID", type = "string")
    private Long orderGoodsId;

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单所属用户ID", type = "string")
    private Long userId;

    @Schema(description = "商品ID（CI-36 填 value_id 的服务端值，不采信入参）", type = "string")
    private Long goodsId;

    @Schema(description = "订单状态码（101/102/201/301/501/103/401/402）")
    private Integer orderStatus;

    @Schema(description = "订单状态文案")
    private String orderStatusText;

    @Schema(description = "行退款状态 0 未退款 / 1 部分退款 / 2 全额退款")
    private Integer refundStatus;

    @Schema(description = "行退款状态文案")
    private String refundStatusText;

    @Schema(description = "已退件数")
    private Integer refundedNumber;

    @Schema(description = "购买件数")
    private Integer number;

    @Schema(description = "是否已评价")
    private Boolean commented;

    @Schema(description = "评论ID；未评价为 null", type = "string")
    private Long commentId;

    @Schema(description = "是否可评价")
    private Boolean canComment;
}
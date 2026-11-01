package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * CN-IN-08 订单尝试完成入参（{@code POST /inner/order/admin/order/{orderId}/try-complete}）。
 * </p>
 * <p>
 * 服务间契约（R2-00 §4.4）：仅 goods 域在 CI-36 评价落库<b>本地事务提交后</b>调用，
 * 用于把「该订单所有未退款行都已评价」的订单从 301（已收货）CAS 跃迁到 501（已完成）。
 * 两个 ID 都必填，JSON 中为字符串化的 Long（全局 Long→String 序列化）。
 * </p>
 */
@Data
@Schema(description = "订单尝试完成入参（服务间）")
public class OrderTryCompleteDTO {

    @Schema(description = "订单行ID（必填，须属于路径上的订单）", type = "string")
    private Long orderGoodsId;

    @Schema(description = "评价ID（必填，写入订单行 comment 字段）", type = "string")
    private Long commentId;
}
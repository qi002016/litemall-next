package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 发表评价出参（05 §2.3.2 CI-36）。
 * </p>
 * <p>
 * {@code orderStatusUpdated}：评价落库提交后是否成功把订单从 301（已收货）推进到 501（已完成）。
 * 该跃迁由 order 域 CN-IN-08 唯一执行（05 §2.3.4），goods 侧失败只记录、<b>不改变评价结果</b>；
 * 因此本字段可能为 {@code false}（残留风险 D-40），前端据此提示「评价成功，订单状态稍后自动完成」。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "发表评价出参")
public class CommentPostResultVO {

    @Schema(description = "新评价ID", type = "string")
    private Long commentId;

    @Schema(description = "订单是否已同步为已完成")
    private Boolean orderStatusUpdated;

    public static CommentPostResultVO of(Long commentId, boolean orderStatusUpdated) {
        CommentPostResultVO vo = new CommentPostResultVO();
        vo.setCommentId(commentId);
        vo.setOrderStatusUpdated(orderStatusUpdated);
        return vo;
    }
}
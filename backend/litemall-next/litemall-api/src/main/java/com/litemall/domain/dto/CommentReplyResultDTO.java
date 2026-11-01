package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端回复评价出参（05 §3.10 CN-30）：{@code {commentId, adminContent, replyTime}}。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "回复评价出参")
public class CommentReplyResultDTO {

    @Schema(description = "评价ID", type = "string")
    private Long commentId;

    @Schema(description = "商家回复内容")
    private String adminContent;

    @Schema(description = "回复时间 yyyy-MM-dd HH:mm:ss")
    private String replyTime;

    public static CommentReplyResultDTO of(Long commentId, String adminContent, String replyTime) {
        CommentReplyResultDTO dto = new CommentReplyResultDTO();
        dto.setCommentId(commentId);
        dto.setAdminContent(adminContent);
        dto.setReplyTime(replyTime);
        return dto;
    }
}
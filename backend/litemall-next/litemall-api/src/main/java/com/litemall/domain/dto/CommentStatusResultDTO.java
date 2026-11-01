package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端隐藏/恢复评价出参（05 §3.11 CN-31）：{@code {commentId, status, statusText}}。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "隐藏/恢复评价出参")
public class CommentStatusResultDTO {

    @Schema(description = "评价ID", type = "string")
    private Long commentId;

    @Schema(description = "目标状态 0=显示 1=已隐藏")
    private Integer status;

    @Schema(description = "状态文案：显示 / 已隐藏")
    private String statusText;

    public static CommentStatusResultDTO of(Long commentId, Integer status, String statusText) {
        CommentStatusResultDTO dto = new CommentStatusResultDTO();
        dto.setCommentId(commentId);
        dto.setStatus(status);
        dto.setStatusText(statusText);
        return dto;
    }
}
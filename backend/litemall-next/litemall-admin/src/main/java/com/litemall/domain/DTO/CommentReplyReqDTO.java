package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端回复评价请求（08 v4 CN-30 {@code POST /admin/comment/reply}，05 §3.10）。
 * <p>
 * {@code content} 必填且去空格非空、1–500 字；覆盖写，{@code replyTime} 每次刷新。
 * 「去空格非空」由 goods 侧判定（admin 只做透传，保证对内/对外同一套规则），
 * 这里只用 {@code @Size} 拦超长，允许纯空白落到 goods 侧报 400。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端回复评价请求")
public class CommentReplyReqDTO {

    @NotNull(message = "评价ID不能为空")
    @Schema(description = "评价ID", type = "string")
    private Long commentId;

    @Size(max = 500, message = "回复内容最多 500 字")
    @Schema(description = "回复内容（1-500 字）")
    private String content;
}
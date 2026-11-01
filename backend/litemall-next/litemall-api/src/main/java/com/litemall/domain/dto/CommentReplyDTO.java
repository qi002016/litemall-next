package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端回复评价入参（05 §3.10 CN-30）：{@code commentId} 必填，{@code content} 必填 1–500。
 * </p>
 * <p>
 * 校验由 goods 侧统一执行（api 模块不带 jakarta.validation，遵循既有约定），
 * admin 只做透传，保证对外/对内同一套判定。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "回复评价入参")
public class CommentReplyDTO {

    @Schema(description = "评价ID", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long commentId;

    @Schema(description = "回复内容 1-500 字", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
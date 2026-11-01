package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端隐藏/恢复评价入参（05 §3.11 CN-31）：{@code commentId} 必填，{@code status} 填<b>目标态</b>
 * （0=显示 / 1=已隐藏），不是切换标记。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "隐藏/恢复评价入参")
public class CommentStatusDTO {

    @Schema(description = "评价ID", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long commentId;

    @Schema(description = "目标状态：0=显示 1=已隐藏", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;
}
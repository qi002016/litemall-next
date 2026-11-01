package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端隐藏/恢复评价请求（08 v4 CN-31 {@code POST /admin/comment/status}，05 §3.11）。
 * <p>
 * {@code status} 是<b>目标态</b>（0=显示、1=已隐藏），不是切换开关：
 * 同一目标态重复提交按幂等成功返回（E-3）。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端隐藏/恢复评价请求")
public class CommentStatusReqDTO {

    @NotNull(message = "评价ID不能为空")
    @Schema(description = "评价ID", type = "string")
    private Long commentId;

    @NotNull(message = "评价状态不能为空")
    @Min(value = 0, message = "评价状态只能是 0（显示）或 1（已隐藏）")
    @Max(value = 1, message = "评价状态只能是 0（显示）或 1（已隐藏）")
    @Schema(description = "目标状态：0=显示，1=已隐藏", type = "integer")
    private Integer status;
}
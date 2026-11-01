package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 * 管理端公告出参（CN-34 列表项，marketing → admin → 前端）。
 * </p>
 * <p>
 * R2（05 §3.12）：
 * </p>
 * <ul>
 *   <li>不含 {@code sortOrder} —— {@code litemall_notice} 无 {@code sort_order} 列，排序固定
 *       {@code add_time DESC, id DESC}（§5.13）；</li>
 *   <li>{@code adminName} 由 marketing 置 {@code null}（marketing 无法读 admin 表，不编造）。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端公告信息")
public class NoticeAdminDTO {

    @Schema(description = "公告ID")
    private Long id;

    @Schema(description = "公告标题")
    private String title;

    @Schema(description = "公告正文（纯文本）")
    private String content;

    @Schema(description = "是否启用：true 展示 / false 停用")
    private Boolean enabled;

    @Schema(description = "创建人ID")
    private Long adminId;

    @Schema(description = "创建人名称：marketing 无法读 admin 表，恒为 null")
    private String adminName;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
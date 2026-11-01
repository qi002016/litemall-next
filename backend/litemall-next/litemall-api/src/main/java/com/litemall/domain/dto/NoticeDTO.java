package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 * 管理端公告写入参（CN-35 新建 / CN-36 编辑，admin → marketing 转发）。
 * </p>
 * <p>
 * R2（05 §3.13/§3.14/§4.3）：
 * </p>
 * <ul>
 *   <li>新建：{@code title} 必填 1–100（去空格非空）、{@code content} 必填 ≤2000 纯文本、
 *       {@code enabled} 默认 {@code true}；<b>不得传 {@code id}</b>；</li>
 *   <li>编辑：{@code id} 必填，{@code title}/{@code content}/{@code enabled} 均可选（未传保持）；
 *       CAS 于 {@code id + update_time}，故 {@code updateTime} 由 admin 透传（可空 ⇒ 服务端读当前值）；</li>
 *   <li>{@code adminId} 由 admin 从网关上下文取值后显式放入本 DTO（对内端点无用户身份，§4.3）。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端公告写入参")
public class NoticeDTO {

    @Schema(description = "公告ID（新建时不得传；编辑时必填）")
    private Long id;

    @Schema(description = "公告标题（1–100）")
    private String title;

    @Schema(description = "公告正文（纯文本 ≤2000）")
    private String content;

    @Schema(description = "是否启用：true 展示 / false 停用")
    private Boolean enabled;

    @Schema(description = "创建公告的管理员ID（由 admin 网关上下文透传，不取客户端 body）")
    private Long adminId;

    @Schema(description = "编辑时的乐观锁基准时间（当前行 update_time）")
    private LocalDateTime updateTime;
}
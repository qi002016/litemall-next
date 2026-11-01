package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * CI-13 操作日志列表项（05-R2 实施口径 §2.5.2 冻结字段集）。
 * </p>
 * <p>
 * 与实体 {@code AdminOperationLog}（表 {@code litemall_log}）的差异：
 * </p>
 * <ul>
 *   <li>新增 {@code typeText}/{@code statusText} 展示文案 —— D-36 冻结：{@code type} 1=业务操作 / 2=登录，
 *       {@code status} 0=成功 / 1=失败；</li>
 *   <li>{@code id}/{@code targetId} 为 string（前端 JS 安全整数与历史大 ID 兼容，§1.5）；</li>
 *   <li>{@code addTime} 固定 {@code yyyy-MM-dd HH:mm:ss}，只出列表需要的字段，不外泄 {@code deleted}。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "操作日志列表项（CI-13）")
public class AdminOperationLogVO {

    @Schema(description = "日志ID", example = "451")
    private String id;

    @Schema(description = "操作管理员账号", example = "admin123")
    private String admin;

    @Schema(description = "来源 IP", example = "127.0.0.1")
    private String ip;

    @Schema(description = "类型：1=业务操作 2=登录", example = "1")
    private Integer type;

    @Schema(description = "类型文案", example = "业务操作")
    private String typeText;

    @Schema(description = "动作（如 POST /admin/order/ship）", example = "POST /admin/order/ship")
    private String action;

    @Schema(description = "状态：0=成功 1=失败", example = "0")
    private Integer status;

    @Schema(description = "状态文案", example = "成功")
    private String statusText;

    @Schema(description = "结果说明", example = "成功")
    private String result;

    @Schema(description = "备注/失败原因", example = "只有待发货订单可以发货")
    private String comment;

    @Schema(description = "目标类型（白名单，可为空）", example = "order")
    private String targetType;

    @Schema(description = "目标ID（可为空）", example = "1024")
    private String targetId;

    @Schema(description = "发生时间（yyyy-MM-dd HH:mm:ss）", example = "2026-11-01 18:43:11")
    private String addTime;
}
package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * CN-37：账号启停返回体（05-R2 实施口径 §3.15）。
 * <p>
 * 出参固定三字段 {@code {id, status, statusText}}；全局 {@code JsonConfig} 把 {@code Long}
 * 序列化为字符串，因此前端拿到的 {@code id} 是字符串（如 {@code "2"}），与列表行一致。
 * </p>
 *
 * @since 2026-11-01
 */
@Schema(description = "账号启停返回体")
public record AdminStatusVO(
        @Schema(description = "账号ID") Long id,
        @Schema(description = "目标状态：1 启用 / 0 禁用") Integer status,
        @Schema(description = "状态文案：启用 / 禁用") String statusText) {

    /** 启用 */
    public static final int ENABLED = 1;
    /** 禁用 */
    public static final int DISABLED = 0;

    public static AdminStatusVO of(Long id, Integer status) {
        return new AdminStatusVO(id, status, text(status));
    }

    /**
     * 状态文案。
     *
     * @param status 1 启用 / 0 禁用
     * @return 启用 / 禁用
     */
    public static String text(Integer status) {
        return status != null && status == ENABLED ? "启用" : "禁用";
    }
}
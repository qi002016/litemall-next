package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端分类新建/修改入参（08 v4 CN-22 / CN-23，对内 {@code /inner/goods/admin/category/*}）。
 * </p>
 * <p>
 * 新建与修改共用一个 DTO：修改是<b>部分更新</b>，未传字段（null）保持原值，由 goods 侧
 * {@code applyNonNull} 语义处理；{@code id} 仅在修改时必填。
 * </p>
 * <p>
 * {@code pid} 在修改时<b>冻结</b>（05 §3.3）：显式传入与库内不同的 {@code pid} ⇒ 400
 * {@code PARAM_INVALID}「本期不支持调整上级分类」。
 * </p>
 * <p>
 * {@code updateTime} 为并发校验回传值（{@code yyyy-MM-dd HH:mm:ss}）：传了即做 CAS，
 * 不一致 ⇒ 409 {@code CONCURRENT_MODIFIED}。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端分类新建/修改入参")
public class CategoryAdminDTO {

    @Schema(description = "分类ID（修改时必填）", type = "string")
    private Long id;

    @Schema(description = "父分类ID，新建时不传默认 0（一级）；修改时冻结", type = "string")
    private Long pid;

    @Schema(description = "分类名称（1–32 字，同级唯一）")
    private String name;

    @Schema(description = "分类图片（≤255）")
    private String picUrl;

    @Schema(description = "分类图标（≤255）")
    private String iconUrl;

    @Schema(description = "排序值（≥0，默认 0）")
    private Integer sortOrder;

    @Schema(description = "分类关键词（≤255）")
    private String keywords;

    @Schema(description = "分类描述（≤255）")
    private String desc;

    @Schema(description = "并发校验回传的更新时间 yyyy-MM-dd HH:mm:ss")
    private String updateTime;
}
package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端修改分类请求（08 v4 CN-23 {@code PUT /admin/category/update}）。
 * <p>
 * 部分更新语义：字段为 {@code null} 表示「不修改」。{@code pid} 本期冻结，
 * 只要传了与现值不同的值即 400 {@code PARAM_INVALID}「本期不支持调整上级分类」（05 §3.3）。
 * {@code updateTime} 用于并发控制：回传列表里拿到的时间，不等则 409 {@code CONCURRENT_MODIFIED}。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端修改分类请求")
public class CategoryUpdateDTO {

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "父分类ID（本期冻结，传不同值即 400）")
    private Long pid;

    @Size(max = 32, message = "分类名称长度不能超过 32 位")
    @Schema(description = "分类名称（≤32，同级唯一）")
    private String name;

    @Size(max = 255, message = "分类图片地址长度不能超过 255 位")
    @Schema(description = "分类图片（≤255）")
    private String picUrl;

    @Size(max = 255, message = "分类图标地址长度不能超过 255 位")
    @Schema(description = "分类图标（≤255）")
    private String iconUrl;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值（≥0）")
    private Integer sortOrder;

    @Size(max = 255, message = "关键字长度不能超过 255 位")
    @Schema(description = "关键字（≤255）")
    private String keywords;

    @Size(max = 255, message = "分类描述长度不能超过 255 位")
    @Schema(description = "分类描述（≤255）")
    private String desc;

    @Schema(description = "并发控制用更新时间（yyyy-MM-dd HH:mm:ss，可空）")
    private String updateTime;
}
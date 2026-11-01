package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端新建分类请求（08 v4 CN-22 {@code POST /admin/category/create}）。
 * <p>
 * 这里是「能便宜拦住」的第一道：必填、长度、排序非负。真正的业务判定（父级是否存在、
 * 是否已有两级、同级是否重名）全部在 goods 侧（05 §4.2：admin 只鉴权 + 转发 + 日志）。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端新建分类请求")
public class CategoryCreateDTO {

    @Schema(description = "父分类ID，缺省 0 表示一级分类")
    private Long pid;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 32, message = "分类名称长度不能超过 32 位")
    @Schema(description = "分类名称（1–32 字，同级唯一）")
    private String name;

    @Size(max = 255, message = "分类图片地址长度不能超过 255 位")
    @Schema(description = "分类图片（≤255）")
    private String picUrl;

    @Size(max = 255, message = "分类图标地址长度不能超过 255 位")
    @Schema(description = "分类图标（≤255）")
    private String iconUrl;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值（≥0，默认 0）")
    private Integer sortOrder;

    @Size(max = 255, message = "关键字长度不能超过 255 位")
    @Schema(description = "关键字（≤255）")
    private String keywords;

    @Size(max = 255, message = "分类描述长度不能超过 255 位")
    @Schema(description = "分类描述（≤255）")
    private String desc;
}
package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * CN-37：管理端账号启停请求（05-R2 实施口径 §3.15）。
 * <p>
 * {@code status}：1 启用 / 0 禁用。步骤 1 的「id 非空、status∈{0,1}」由本类注解先行拦截，
 * 控制器内仍做一次显式校验，保证直接调用（单测/内部调用）时同样得到 {@code 400 PARAM_INVALID}。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端账号启停请求")
public class AdminStatusDTO {

    @NotNull(message = "账号ID不能为空")
    @Schema(description = "账号ID")
    private Long id;

    @NotNull(message = "账号状态不能为空")
    @Min(value = 0, message = "账号状态只能是 0 或 1")
    @Max(value = 1, message = "账号状态只能是 0 或 1")
    @Schema(description = "目标状态：1 启用 / 0 禁用")
    private Integer status;
}
package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端用户状态变更请求 DTO（H5：admin 不再直写 litemall_user）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端用户状态变更请求")
public class UserStatusDTO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "目标状态：0 可用，1 禁用，2 注销")
    private Integer status;
}
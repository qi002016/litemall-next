package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端用户行 / 用户状态变更 DTO（H5：admin 不再直写 litemall_user）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端用户信息")
public class UserAdminDTO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "用户等级：0 普通，1 VIP，2 高级VIP")
    private Integer userLevel;

    @Schema(description = "状态：0 可用，1 禁用，2 注销")
    private Integer status;

    @Schema(description = "注册时间")
    private java.time.LocalDateTime addTime;
}
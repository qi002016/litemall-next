package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * M13：新建管理端账号请求（原为 {@code Map<String, String>}，且空值只在控制器里手写 if 判断）。
 * <p>
 * 注意：这里新增了密码长度下界 6 位，属于**对外契约变更**——管理端页面没有对应的前端校验，
 * 之前能提交的短口令现在会被 400 拒绝（错误信息为"密码长度必须为 6~64 位"）。
 */
@Data
@Schema(description = "新建管理端账号请求")
public class AdminCreateDTO {

    @NotBlank(message = "账号不能为空")
    @Size(max = 64, message = "账号长度不能超过 64 位")
    @Schema(description = "账号")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须为 6~64 位")
    @Schema(description = "初始密码，6~64 位")
    private String password;

    @Size(max = 255, message = "头像地址长度不能超过 255 位")
    @Schema(description = "头像地址（可空）")
    private String avatar;
}
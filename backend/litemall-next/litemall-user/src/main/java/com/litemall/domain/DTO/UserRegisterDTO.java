package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "注册表单实体")
public class UserRegisterDTO {
    @Schema(description = "用户名")
    @NotNull(message = "用户名不能为空")
    private String username;
    @NotNull(message = "密码不能为空")
    @Schema(description = "密码")
    private String password;
    @NotNull(message = "手机号不能为空")
    @Schema(description = "手机号")
    private String mobile;
    @NotNull(message = "验证码不能为空")
    @Schema(description = "验证码")
    private String code;
}

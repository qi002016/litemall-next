package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "验证码请求实体")
public class CaptchaDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @NotBlank(message = "验证码类型不能为空")
    @Pattern(regexp = "^(register|login)$", message = "验证码类型只能是 register 或 login")
    @Schema(description = "验证码类型:register-注册,login-登录")
    private String type;
}

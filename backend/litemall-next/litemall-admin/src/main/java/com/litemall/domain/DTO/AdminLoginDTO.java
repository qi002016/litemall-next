package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * M13：管理端登录请求（原为 {@code Map<String, String>}）。
 */
@Data
@Schema(description = "管理端登录请求")
public class AdminLoginDTO {

    @NotBlank(message = "账号不能为空")
    @Size(max = 64, message = "账号长度不能超过 64 位")
    @Schema(description = "账号")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过 64 位")
    @Schema(description = "密码")
    private String password;
}
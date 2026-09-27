package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * M13：重置管理端账号口令请求（原为 {@code Map<String, Object>}，
 * 且 {@code id} 用 {@code asLong()} 手工解析、{@code password} 为 null 时会 NPE）。
 */
@Data
@Schema(description = "重置管理端账号口令请求")
public class AdminResetPwdDTO {

    @NotNull(message = "账号ID不能为空")
    @Schema(description = "账号ID")
    private Long id;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须为 6~64 位")
    @Schema(description = "新密码，6~64 位")
    private String password;
}
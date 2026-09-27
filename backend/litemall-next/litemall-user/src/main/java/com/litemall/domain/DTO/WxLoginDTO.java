package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信小程序登录请求")
public class WxLoginDTO {

    @NotBlank(message = "code 不能为空")
    @Schema(description = "wx.login() 返回的临时登录凭证")
    private String code;

    @Schema(description = "用户昵称(前端 getUserProfile 获取)")
    private String nickname;

    @Schema(description = "用户头像 URL")
    private String avatarUrl;

    @Schema(description = "性别:0-未知,1-男,2-女")
    private Integer gender;
}

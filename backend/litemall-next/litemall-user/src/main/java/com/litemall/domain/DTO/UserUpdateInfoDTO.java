package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新用户信息")
public class UserUpdateInfoDTO {

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像 URL")
    private String avatar;

    @Schema(description = "性别:0-未知,1-男,2-女")
    private Integer gender;
}

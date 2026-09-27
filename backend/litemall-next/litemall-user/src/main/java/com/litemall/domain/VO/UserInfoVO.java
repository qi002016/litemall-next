package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "用户信息实体类")
public class UserInfoVO {

    @Schema(description = "用户 ID")
    private long userId;    //用户 ID

    @Schema(description = "用户名")
    private String username;   //用户名

    @Schema(description = "昵称")
    private String nickname;   //昵称

    @Schema(description = "头像 URL")
    private String avatar;     //头像 URL

    @Schema(description = "性别")
    private int gender;     //性别

    @Schema(description = "手机号")
    private String mobile;   //手机号

    @Schema(description = "用户等级")
    private int userLevel;  //用户等级

}

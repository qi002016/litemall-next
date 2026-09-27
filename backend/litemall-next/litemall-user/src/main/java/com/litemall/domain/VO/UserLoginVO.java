package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户登录信息")
public class UserLoginVO {
    @Schema(description = "token")
    private String token;
    @Schema(description = "用户ID", type = "string")
    private Long userId;
    @Schema(description = "用户名")
    private String username;
}

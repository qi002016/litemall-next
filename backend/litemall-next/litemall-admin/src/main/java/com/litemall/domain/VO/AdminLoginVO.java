package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * M13：管理端登录响应（原为 {@code Map<String, Object>}）。
 * <p>
 * 字段名与改造前逐字保持一致（token/id/username/avatar），故管理端前端无需改动。
 */
@Data
@Schema(description = "管理端登录响应")
public class AdminLoginVO {

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "账号ID")
    private Long id;

    @Schema(description = "账号")
    private String username;

    @Schema(description = "头像地址")
    private String avatar;
}
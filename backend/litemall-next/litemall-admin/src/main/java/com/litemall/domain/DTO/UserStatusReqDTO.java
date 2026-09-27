package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * M13：管理端用户状态变更请求（原为 {@code Map<String, Object>}）。
 * <p>
 * {@code status} 取值 0 可用 / 1 禁用 / 2 注销，与 user 服务的 {@code UserStatus} 枚举一致；
 * 这里先按范围挡掉明显非法的值，具体语义仍由 user 服务收口。
 */
@Data
@Schema(description = "管理端用户状态变更请求")
public class UserStatusReqDTO {

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID")
    private Long id;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态取值只能是 0/1/2")
    @Max(value = 2, message = "状态取值只能是 0/1/2")
    @Schema(description = "目标状态：0 可用，1 禁用，2 注销")
    private Integer status;
}
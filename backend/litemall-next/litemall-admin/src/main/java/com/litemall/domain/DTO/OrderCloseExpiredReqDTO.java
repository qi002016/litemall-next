package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理端超时关单请求（CN-11 {@code POST /admin/order/close-expired}）。
 * <p>
 * {@code limit} 单批上限 1–200，缺省 100；{@code dryRun} 缺省 false。
 * 对内 order 侧走 query 参数，由 admin 转换（见 {@code OrderController#closeExpired}）。
 * </p>
 */
@Data
@Schema(description = "管理端超时关单请求")
public class OrderCloseExpiredReqDTO {

    @Min(value = 1, message = "limit 取值范围 1~200")
    @Max(value = 200, message = "limit 取值范围 1~200")
    @Schema(description = "单批上限 1–200，缺省 100")
    private Integer limit;

    @Schema(description = "只扫描不落库，缺省 false")
    private Boolean dryRun;
}
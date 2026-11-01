package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * CN-IN-08 订单尝试完成出参（裸 DTO，不加 {@code Result} 包装）。
 * </p>
 * <p>
 * {@code changed=true} 表示本次调用真的把订单从 301 CAS 到 501；
 * {@code changed=false} 表示幂等命中（已评价 / 已非 301），仍返回 200，调用方无需重试。
 * </p>
 */
@Data
@Schema(description = "订单尝试完成出参（服务间）")
public class OrderTryCompleteResultDTO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "调用后的订单状态码（回读值，301/501 等）")
    private Integer orderStatus;

    @Schema(description = "本次是否发生 301 → 501 跃迁")
    private Boolean changed;
}
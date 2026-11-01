package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 售后退款请求DTO（CN-IN-02：order 服务内部调用 pay 服务）
 * </p>
 * <p>
 * 金额口径为整数分（06 §4）。{@code aftersaleId} 是幂等键：同一售后单重复调用只产生一笔退款。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后退款请求")
public class PayRefundDTO {

    @Schema(description = "售后单ID（幂等键，必填）", type = "string")
    private Long aftersaleId;

    @Schema(description = "订单ID（必填）", type = "string")
    private Long orderId;

    @Schema(description = "退款金额（整数分，>0 且不超过订单实付）")
    private Integer amount;

    @Schema(description = "退款原因（可选，≤255）")
    private String reason;

    @Schema(description = "操作人ID（可选，仅日志用）", type = "string")
    private Long operatorId;
}
package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 售后退款编排出参（CN-07 / CN-08）。回补失败时 {@code status} 停在 refunding 且
 * {@code restockPending=true}（HTTP 仍为 200，不返回 409）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后退款出参")
public class AftersaleRefundResultDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "售后状态标识")
    private String status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "退款单ID（litemall_refund.id）", type = "string")
    private Long refundId;

    @Schema(description = "退款金额（整数分）")
    private Integer refundAmount;

    @Schema(description = "退款完成时间（yyyy-MM-dd HH:mm:ss）")
    private String refundTime;

    @Schema(description = "回补状态：0 未回补 / 1 已回补 / 2 待回补")
    private Integer restockStatus;

    @Schema(description = "回补状态文案")
    private String restockStatusText;

    @Schema(description = "是否待回补")
    private Boolean restockPending;

    @Schema(description = "订单行退款状态：0 未退款 / 1 部分退款 / 2 已全额退款")
    private Integer refundStatus;

    @Schema(description = "订单行退款状态文案")
    private String refundStatusText;
}

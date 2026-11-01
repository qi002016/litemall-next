package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 售后审核通过出参（CN-03）。type=1 时 returnAddress/returnDeadline 为 null。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后审核通过出参")
public class AftersaleApproveResultDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "售后状态标识")
    private String status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "退货地址（仅退货退款）")
    private String returnAddress;

    @Schema(description = "待寄回截止时间（yyyy-MM-dd HH:mm:ss）")
    private String returnDeadline;
}

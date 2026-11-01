package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 售后确认收货 / 跳过收货出参（CN-05 / CN-06）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后收货出参")
public class AftersaleReceiveResultDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "售后状态标识")
    private String status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "确认收货时间（yyyy-MM-dd HH:mm:ss）")
    private String receiveTime;

    @Schema(description = "是否跳过收货（CN-06）")
    private Boolean skippedReceive;
}

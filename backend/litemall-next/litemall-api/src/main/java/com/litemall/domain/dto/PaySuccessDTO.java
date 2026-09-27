package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 支付成功回调DTO（pay 服务通知 order 更新支付状态）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "支付成功回调")
public class PaySuccessDTO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "微信付款单号")
    private String payId;
}

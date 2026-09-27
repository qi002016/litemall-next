package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单支付信息DTO（pay 服务查询订单支付状态用）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "订单支付信息")
public class OrderPayInfoDTO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "用户ID", type = "string")
    private Long userId;

    /**
     * 订单状态：101 未付款；102 待发货；201 已发货；301 已收货；401/402 售后
     */
    @Schema(description = "订单状态")
    private Integer orderStatus;

    @Schema(description = "实付金额")
    private BigDecimal actualPrice;

    @Schema(description = "微信付款时间")
    private LocalDateTime payTime;

    @Schema(description = "微信付款单号")
    private String payId;
}

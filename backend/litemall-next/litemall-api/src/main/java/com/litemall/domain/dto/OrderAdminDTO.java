package com.litemall.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 * 管理端订单列表行 DTO（CI-26 / CI-99 共用，07 §3.1）。
 * </p>
 * <p>
 * R1 变更：
 * <ul>
 *   <li>{@code orderAmount} 改 <b>int 分</b>（06 §4 金额出参口径）；</li>
 *   <li>新增 {@code statusText}、{@code fullyRefunded}、{@code refundedAmount}、
 *       {@code aftersaleStatus/Text}、{@code goodsSummary}；</li>
 *   <li>{@code id} 沿用现状字段名（P-16：不改成 orderId），与 C 端 {@code orderId} 同义。</li>
 * </ul>
 * </p>
 */
@Data
@Schema(description = "管理端订单列表行")
public class OrderAdminDTO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "订单金额（分）")
    private Integer orderAmount;

    /** 出参标识：unpaid/undelivered/shipped/confirmed/completed/canceled/refund/refunded */
    @Schema(description = "订单状态标识")
    private String status;

    @Schema(description = "订单状态文案：未付款/待发货/已发货/已收货/已完成/已关闭/退款中/已退款")
    private String statusText;

    @Schema(description = "是否已全额退款")
    private Boolean fullyRefunded;

    @Schema(description = "已退款金额（分）")
    private Integer refundedAmount;

    @Schema(description = "本单最近一笔未完结售后状态标识；无则 null")
    private String aftersaleStatus;

    @Schema(description = "售后状态文案；无则 null")
    private String aftersaleStatusText;

    @Schema(description = "商品简述，如「商品A 等 3 件」")
    private String goodsSummary;

    @Schema(description = "下单时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime addTime;
}
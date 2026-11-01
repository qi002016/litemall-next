package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 订单列表项VO（CI-83，07 §2.3）。金额一律 <b>int 分</b>。
 * <p>
 * {@code goodsList} 为现状保留字段（P-12），行内新增 {@code refundStatus/refundStatusText}；
 * {@code expireTime} 仅未付款(101)有值，其余状态为 null。
 * </p>
 */
@Data
@Schema(description = "订单列表项VO")
public class OrderListItemVO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "状态标识：unpaid/undelivered/shipped/confirmed/completed/canceled/refund/refunded")
    private String status;

    @Schema(description = "状态文案：未付款/待发货/已发货/已收货/已完成/已关闭/退款中/已退款")
    private String statusText;

    @Schema(description = "商品总额（分）")
    private Integer goodsAmount;

    @Schema(description = "运费（分）")
    private Integer freightAmount;

    @Schema(description = "优惠券抵扣（分）")
    private Integer couponAmount;

    @Schema(description = "应付金额（分）")
    private Integer orderAmount;

    @Schema(description = "是否已全额退款")
    private Boolean fullyRefunded;

    @Schema(description = "已退款金额（分）")
    private Integer refundedAmount;

    @Schema(description = "是否还有未评价行（决定是否进「待评价」栏）")
    private Boolean hasUnreviewed;

    @Schema(description = "未付款自动关闭时刻；非 101 为 null")
    private String expireTime;

    @Schema(description = "下单时间（yyyy-MM-dd HH:mm:ss）")
    private String addTime;

    @Schema(description = "订单商品简述数组（现状保留，P-12）")
    private List<OrderGoodsVO> goodsList;
}
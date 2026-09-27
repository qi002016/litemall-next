package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 订单列表项VO（4.7）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "订单列表项VO")
public class OrderListItemVO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "订单状态：unpaid/undelivered/shipped/confirmed/refund")
    private String status;

    @Schema(description = "商品总额")
    private BigDecimal goodsAmount;

    @Schema(description = "运费")
    private BigDecimal freightAmount;

    @Schema(description = "优惠券抵扣")
    private BigDecimal couponAmount;

    @Schema(description = "应付金额")
    private BigDecimal orderAmount;

    @Schema(description = "实付金额")
    private BigDecimal actualPrice;

    @Schema(description = "订单商品简述数组")
    private List<OrderGoodsVO> goodsList;

    @Schema(description = "下单时间（yyyy-MM-dd HH:mm:ss）")
    private String addTime;
}

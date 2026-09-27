package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 订单详情VO（4.8）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "订单详情VO")
public class OrderDetailVO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "订单状态：unpaid/undelivered/shipped/confirmed/refund")
    private String status;

    @Schema(description = "收货人")
    private String consignee;

    @Schema(description = "收货电话")
    private String mobile;

    @Schema(description = "完整收货地址")
    private String address;

    @Schema(description = "订单留言")
    private String message;

    @Schema(description = "商品总额")
    private BigDecimal goodsAmount;

    @Schema(description = "运费")
    private BigDecimal freightAmount;

    @Schema(description = "优惠券抵扣")
    private BigDecimal couponAmount;

    @Schema(description = "订单金额")
    private BigDecimal orderAmount;

    @Schema(description = "实付金额")
    private BigDecimal actualPrice;

    @Schema(description = "支付时间；未付为 null")
    private String payTime;

    @Schema(description = "快递公司；未发为 null")
    private String shipChannel;

    @Schema(description = "运单号；未发为 null")
    private String shipSn;

    @Schema(description = "发货时间；未发为 null")
    private String shipTime;

    @Schema(description = "下单时间")
    private String addTime;

    @Schema(description = "订单商品数组")
    private List<OrderGoodsVO> goodsList;
}

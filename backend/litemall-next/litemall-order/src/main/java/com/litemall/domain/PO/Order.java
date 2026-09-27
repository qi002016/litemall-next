package com.litemall.domain.PO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_order")
@Schema(description = "订单表")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "订单编号")
    private String orderSn;

    /**
     * 订单状态：101 未付款；102 已付款/待发货；201 已发货；301 已收货；401/402 售后
     */
    @Schema(description = "订单状态：101 未付款；102 待发货；201 已发货；301 已收货；401/402 售后")
    private Integer orderStatus;

    /**
     * 售后状态：0 可申请；1 用户已申请；2 审核通过；3 退款成功；4 审核拒绝；5 用户已取消
     */
    @Schema(description = "售后状态：0 可申请；1 用户已申请；2 审核通过；3 退款成功；4 审核拒绝；5 用户已取消")
    private Integer aftersaleStatus;

    @Schema(description = "收货人名称")
    private String consignee;

    @Schema(description = "收货人手机号")
    private String mobile;

    @Schema(description = "收货具体地址")
    private String address;

    @Schema(description = "用户订单留言")
    private String message;

    @Schema(description = "商品总费用")
    private BigDecimal goodsPrice;

    @Schema(description = "配送费用")
    private BigDecimal freightPrice;

    @Schema(description = "优惠券减免")
    private BigDecimal couponPrice;

    @Schema(description = "用户积分减免")
    private BigDecimal integralPrice;

    @Schema(description = "团购优惠价减免")
    private BigDecimal grouponPrice;

    @Schema(description = "订单费用 = goods_price + freight_price - coupon_price")
    private BigDecimal orderPrice;

    @Schema(description = "实付费用 = order_price - integral_price")
    private BigDecimal actualPrice;

    @Schema(description = "微信付款编号")
    private String payId;

    @Schema(description = "微信付款时间")
    private LocalDateTime payTime;

    @Schema(description = "发货编号")
    private String shipSn;

    @Schema(description = "发货快递公司")
    private String shipChannel;

    @Schema(description = "发货开始时间")
    private LocalDateTime shipTime;

    @Schema(description = "实际退款金额")
    private BigDecimal refundAmount;

    @Schema(description = "退款方式")
    private String refundType;

    @Schema(description = "退款备注")
    private String refundContent;

    @Schema(description = "退款时间")
    private LocalDateTime refundTime;

    @Schema(description = "用户确认收货时间")
    private LocalDateTime confirmTime;

    @Schema(description = "待评价订单商品数量")
    private Integer comments;

    @Schema(description = "订单关闭时间")
    private LocalDateTime endTime;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}

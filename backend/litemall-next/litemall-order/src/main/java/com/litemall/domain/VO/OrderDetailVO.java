package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 订单详情VO（CI-84，07 §2.4）。金额一律 <b>int 分</b>。
 * <p>
 * R1 变更：新增 {@code statusText/expireTime/fullyRefunded/refundedAmount/lines/timeline/aftersale}；
 * {@code goodsList} 保留并存（标 {@code @Deprecated}，P-12 只增不删）。
 * </p>
 */
@Data
@Schema(description = "订单详情VO")
public class OrderDetailVO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "状态标识")
    private String status;

    @Schema(description = "状态文案")
    private String statusText;

    @Schema(description = "收货人")
    private String consignee;

    @Schema(description = "收货电话")
    private String mobile;

    @Schema(description = "完整收货地址")
    private String address;

    @Schema(description = "订单留言")
    private String message;

    @Schema(description = "商品总额（分）")
    private Integer goodsAmount;

    @Schema(description = "运费（分）")
    private Integer freightAmount;

    @Schema(description = "优惠券抵扣（分）")
    private Integer couponAmount;

    @Schema(description = "订单金额（分）")
    private Integer orderAmount;

    @Schema(description = "实付金额（分）")
    private Integer actualPrice;

    @Schema(description = "支付时间；未付为 null")
    private String payTime;

    @Schema(description = "快递公司；未发为 null")
    private String shipChannel;

    @Schema(description = "运单号；未发为 null")
    private String shipSn;

    @Schema(description = "发货时间；未发为 null")
    private String shipTime;

    @Schema(description = "未付款自动关闭时刻；非 101 为 null")
    private String expireTime;

    @Schema(description = "下单时间")
    private String addTime;

    @Schema(description = "是否已全额退款")
    private Boolean fullyRefunded;

    @Schema(description = "已退款金额（分）")
    private Integer refundedAmount;

    @Schema(description = "行明细（含售后可申请信息）")
    private List<OrderDetailLineVO> lines;

    @Schema(description = "时间线")
    private List<OrderTimelineVO> timeline;

    @Schema(description = "当前未完结售后摘要；无则 null")
    private OrderAftersaleBriefVO aftersale;

    @Deprecated(since = "R1")
    @Schema(description = "订单商品数组（保留兼容，P-12；新代码请用 lines）")
    private List<OrderGoodsVO> goodsList;
}
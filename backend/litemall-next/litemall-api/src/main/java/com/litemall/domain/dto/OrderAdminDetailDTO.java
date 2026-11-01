package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * CN-33 管理端订单详情出参（{@code GET /inner/order/admin/order/{id}}；admin 侧包装为
 * {@code GET /admin/order/{id}}）。
 * </p>
 * <p>
 * 所有金额字段为 <b>int 分</b>；{@code shipUpdateCount/shipUpdateTime} 由 {@code litemall_log}
 * 派生（P-22，零加列）；{@code canUpdateShip = status=shipped 且 shipUpdateCount=0}（P-21）。
 * {@code lines[]} 是 R1 的新结构（同时是 C 端 CI-84 {@code lines} 的来源），
 * 行级售后摘要取该行「当前未完结」的一笔。
 * </p>
 */
@Data
@Schema(description = "管理端订单详情")
public class OrderAdminDetailDTO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "C 端是否已隐藏（user_deleted）")
    private Boolean userDeleted;

    @Schema(description = "订单状态标识")
    private String status;

    @Schema(description = "订单状态文案")
    private String statusText;

    @Schema(description = "收货人")
    private String consignee;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "收货地址")
    private String address;

    @Schema(description = "用户留言")
    private String message;

    @Schema(description = "商品总额（分）")
    private Integer goodsAmount;

    @Schema(description = "运费（分）")
    private Integer freightAmount;

    @Schema(description = "优惠金额（分）")
    private Integer couponAmount;

    @Schema(description = "应付金额（分）")
    private Integer orderAmount;

    @Schema(description = "实付金额（分）")
    private Integer actualPrice;

    @Schema(description = "支付时间 yyyy-MM-dd HH:mm:ss")
    private String payTime;

    @Schema(description = "未付款自动关闭时刻 yyyy-MM-dd HH:mm:ss；非 101 为 null")
    private String expireTime;

    @Schema(description = "快递公司")
    private String shipChannel;

    @Schema(description = "快递单号")
    private String shipSn;

    @Schema(description = "发货时间 yyyy-MM-dd HH:mm:ss")
    private String shipTime;

    @Schema(description = "物流信息修改次数（litemall_log 派生，P-22）")
    private Integer shipUpdateCount;

    @Schema(description = "是否允许修改物流：status=shipped 且 shipUpdateCount=0（P-21）")
    private Boolean canUpdateShip;

    @Schema(description = "关闭方式：1 用户取消 / 2 超时 / 3 运营关单；非 103 为 null")
    private Integer closeType;

    @Schema(description = "关闭方式文案")
    private String closeTypeText;

    @Schema(description = "是否已全额退款")
    private Boolean fullyRefunded;

    @Schema(description = "已退款金额（分）")
    private Integer refundedAmount;

    @Schema(description = "下单时间 yyyy-MM-dd HH:mm:ss")
    private String addTime;

    @Schema(description = "订单行（含行级售后摘要）")
    private java.util.List<Line> lines;

    @Data
    @Schema(description = "管理端订单行")
    public static class Line {

        @Schema(description = "订单行ID")
        private Long orderGoodsId;

        @Schema(description = "货品/SKU ID", type = "string")
        private Long productId;

        @Schema(description = "商品ID", type = "string")
        private Long goodsId;

        @Schema(description = "商品名称")
        private String goodsName;

        @Schema(description = "规格文案，如「黑色 / L」")
        private String specText;

        @Schema(description = "成交单价（分）")
        private Integer price;

        @Schema(description = "数量")
        private Integer number;

        @Schema(description = "行实付（分，已扣该行券分摊）")
        private Integer actualAmount;

        @Schema(description = "已退件数")
        private Integer refundedNumber;

        @Schema(description = "行退款状态 0 未退款 / 1 部分退款 / 2 全额退款")
        private Integer refundStatus;

        @Schema(description = "行退款状态文案")
        private String refundStatusText;

        @Schema(description = "该行当前未完结售后ID；无则 null", type = "string")
        private Long aftersaleId;

        @Schema(description = "该行当前未完结售后状态；无则 null")
        private String aftersaleStatus;

        @Schema(description = "该行当前未完结售后状态文案；无则 null")
        private String aftersaleStatusText;
    }
}
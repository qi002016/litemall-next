package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 管理端售后详情（CN-02，07 §4.6）：售后字段 + 关联订单摘要 + 订单行摘要 + 时间线。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端售后详情")
public class AftersaleAdminDetailDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "售后编号")
    private String aftersaleSn;

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "订单行ID", type = "string")
    private Long orderGoodsId;

    @Schema(description = "用户ID", type = "string")
    private Long userId;

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "商品图片")
    private String picUrl;

    @Schema(description = "规格文案")
    private String specText;

    @Schema(description = "申请退款数量")
    private Integer number;

    @Schema(description = "售后类型：1 仅退款 / 2 退货退款（0 为历史只读）")
    private Integer type;

    @Schema(description = "售后类型文案")
    private String typeText;

    @Schema(description = "售后状态标识")
    private String status;

    @Schema(description = "售后状态文案")
    private String statusText;

    @Schema(description = "退款金额（整数分）")
    private Integer amount;

    @Schema(description = "申请原因")
    private String reason;

    @Schema(description = "用户说明")
    private String comment;

    @Schema(description = "凭证图片（恒为数组）")
    private List<String> images = new ArrayList<>();

    @Schema(description = "退货地址（仅退货退款；全局配置，不落库）")
    private String returnAddress;

    @Schema(description = "退货物流公司")
    private String returnShipCompany;

    @Schema(description = "退货物流单号")
    private String returnShipSn;

    @Schema(description = "用户填单号时间（yyyy-MM-dd HH:mm:ss）")
    private String returnShipTime;

    @Schema(description = "待寄回截止时间（yyyy-MM-dd HH:mm:ss）")
    private String returnDeadline;

    @Schema(description = "运营确认收货时间（yyyy-MM-dd HH:mm:ss）")
    private String receiveTime;

    @Schema(description = "审核时间（litemall_aftersale.handle_time）")
    private String auditTime;

    @Schema(description = "回补状态：0 未回补 / 1 已回补 / 2 待回补")
    private Integer restockStatus;

    @Schema(description = "回补状态文案")
    private String restockStatusText;

    @Schema(description = "是否待回补（由 restock_status=2 派生）")
    private Boolean restockPending;

    @Schema(description = "退款完成时间（litemall_refund.refund_time）")
    private String refundTime;

    @Schema(description = "关闭/驳回原因")
    private String closeReason;

    @Schema(description = "审核操作人 admin.id", type = "string")
    private Long auditAdmin;

    @Schema(description = "时间线（升序）")
    private List<Timeline> timeline = new ArrayList<>();

    @Schema(description = "关联订单摘要")
    private OrderBrief order;

    @Schema(description = "关联订单行摘要")
    private Line line;

    /** 时间线节点：time / actor(user|admin|system) / actorText / action */
    @Data
    @Schema(description = "售后时间线节点")
    public static class Timeline {

        @Schema(description = "发生时间（yyyy-MM-dd HH:mm:ss）")
        private String time;

        @Schema(description = "操作者类型：user / admin / system")
        private String actor;

        @Schema(description = "操作者文案")
        private String actorText;

        @Schema(description = "动作文案")
        private String action;
    }

    /** 关联订单摘要（手机号脱敏） */
    @Data
    @Schema(description = "售后关联订单摘要")
    public static class OrderBrief {

        @Schema(description = "订单ID", type = "string")
        private Long orderId;

        @Schema(description = "订单编号")
        private String orderSn;

        @Schema(description = "订单状态标识（unpaid/undelivered/shipped/confirmed/completed/canceled/refund/refunded）")
        private String status;

        @Schema(description = "订单状态文案")
        private String statusText;

        @Schema(description = "订单实付（整数分）")
        private Integer orderAmount;

        @Schema(description = "已退金额（整数分）")
        private Integer refundedAmount;

        @Schema(description = "是否已全额退款")
        private Boolean fullyRefunded;

        @Schema(description = "收货人")
        private String consignee;

        @Schema(description = "手机号（中间四位脱敏）")
        private String mobile;

        @Schema(description = "收货地址")
        private String address;
    }

    /** 关联订单行摘要 */
    @Data
    @Schema(description = "售后关联订单行摘要")
    public static class Line {

        @Schema(description = "订单行ID", type = "string")
        private Long orderGoodsId;

        @Schema(description = "商品名称")
        private String goodsName;

        @Schema(description = "规格文案")
        private String specText;

        @Schema(description = "购买数量")
        private Integer number;

        @Schema(description = "单价（整数分）")
        private Integer price;

        @Schema(description = "行实付（整数分）")
        private Integer actualAmount;

        @Schema(description = "已退数量")
        private Integer refundedNumber;

        @Schema(description = "行退款状态：0 未退 / 1 部分退 / 2 全退")
        private Integer refundStatus;
    }
}
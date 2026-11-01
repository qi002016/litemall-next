package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 售后详情（CN-16 / 07 §4.3）：列表字段 + 凭证、退货信息、时间线。
 * <p>{@code images} 恒为数组（无凭证时为空数组，不为 null）；{@code returnAddress} 仅退货退款返回。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "售后详情")
public class AftersaleDetailVO extends AftersaleListItemVO {

    @Schema(description = "售后原因")
    private String reason;

    @Schema(description = "补充说明")
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

    @Schema(description = "审核时间（yyyy-MM-dd HH:mm:ss，取 handle_time）")
    private String auditTime;

    @Schema(description = "是否待回补（由 restock_status=2 派生）")
    private Boolean restockPending;

    @Schema(description = "退款完成时间（litemall_refund.refund_time）")
    private String refundTime;

    @Schema(description = "关闭/驳回原因")
    private String closeReason;

    @Schema(description = "时间线（升序）")
    private List<OrderTimelineVO> timeline = new ArrayList<>();
}

package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 支付结果VO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "支付结果")
public class PayResultVO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    /** unpaid / paid / refunded */
    @Schema(description = "支付状态：unpaid / paid / refunded")
    private String status;

    @Schema(description = "支付时间；未支付为 null")
    private String payTime;

    @Schema(description = "微信支付单号；未支付为 null")
    private String payId;

    /**
     * CI-106 新增：支付结果 success / fail / pending
     * （尚未支付含已关闭一律 pending；R1 不落失败态，演示 fail 不改变订单状态）
     */
    @Schema(description = "支付结果：success / fail / pending")
    private String result;

    /**
     * CI-106 新增：订单主状态码
     */
    @Schema(description = "订单主状态码：101 未付款 / 102 待发货 / 201 已发货 / 301 已收货 / 501 已完成 / 103 已关闭 / 401 退款中 / 402 已退款")
    private Integer orderStatus;

    /**
     * CI-106 新增：订单主状态文案
     */
    @Schema(description = "订单主状态文案")
    private String orderStatusText;

    /**
     * CI-106 新增：支付截止时间（yyyy-MM-dd HH:mm:ss）；暂无数据源时为 null，待订单侧透出 expire_time 后接线
     */
    @Schema(description = "支付截止时间；无数据源时为 null")
    private String expireTime;
}

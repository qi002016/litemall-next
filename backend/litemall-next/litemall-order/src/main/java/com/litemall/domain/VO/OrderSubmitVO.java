package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 提交订单结果VO（CI-82，07 §2.2）。
 * </p>
 * <p>{@code expireTime} = 下单时刻 + 30 分钟，是 CN-11 超时关单与 CI-103 支付校验的唯一依据。</p>
 */
@Data
@Schema(description = "提交订单结果VO")
public class OrderSubmitVO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "应付金额（分）")
    private Integer orderAmount;

    @Schema(description = "未付款自动关闭时刻（yyyy-MM-dd HH:mm:ss）")
    private String expireTime;
}
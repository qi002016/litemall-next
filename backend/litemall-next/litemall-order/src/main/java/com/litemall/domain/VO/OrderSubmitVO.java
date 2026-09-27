package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * <p>
 * 提交订单结果VO（4.6）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "提交订单结果VO")
public class OrderSubmitVO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "应付金额")
    private BigDecimal orderAmount;
}

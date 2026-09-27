package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "订单确认试算VO")
public class OrderConfirmVO {

    @Schema(description = "商品总额")
    private BigDecimal goodsAmount;

    @Schema(description = "运费")
    private BigDecimal freightAmount;

    @Schema(description = "优惠券抵扣")
    private BigDecimal couponAmount;

    @Schema(description = "应付总额")
    private BigDecimal orderAmount;
}

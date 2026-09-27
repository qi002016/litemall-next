package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "优惠券用户信息")
public class CouponUserVO {

    @Schema(description = "用户券记录 ID")
    private Long id;

    @Schema(description = "优惠券 ID")
    private Long couponId;

    @Schema(description = "券名")
    private String name;

    @Schema(description = "优惠金额")
    private BigDecimal discount;

    @Schema(description = "使用门槛")
    private BigDecimal min;

    @Schema(description = "过期时间")
    private String endTime;
}

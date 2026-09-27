package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema
public class CouponVO {
    @Schema(description = "优惠券 ID", type = "string")
    private Long id;

    @Schema(description = "优惠券名称")
    private String name;

    @Schema(description = "使用限制说明")
    private String desc;

    @Schema(description = "标签（如'新人专用'）")
    private String tag;

    @Schema(description = "优惠金额（元）")
    private BigDecimal discount;

    @Schema(description = "使用门槛（满多少元可用）")
    private BigDecimal min;

    @Schema(description = "有效期开始（yyyy-MM-dd HH:mm:ss）")
    private String startTime;

    @Schema(description = "有效期截止")
    private String endTime;

    @Schema(description = "剩余可领数量；-1 表示不限量")
    private Integer leftCount;

}

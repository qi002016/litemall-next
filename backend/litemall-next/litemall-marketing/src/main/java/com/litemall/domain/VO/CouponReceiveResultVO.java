package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "领券结果")
public class CouponReceiveResultVO {

    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @Schema(description = "状态：PROCESSING 排队中 / SUCCESS 成功 / FAILED 失败")
    private String status;

    @Schema(description = "失败原因")
    private String message;

    @Schema(description = "成功时的用户券记录")
    private CouponUserVO couponUser;
}

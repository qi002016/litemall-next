package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "领取优惠券请求")
public class CouponReceiveDTO {

    @NotNull(message = "优惠券 ID 不能为空")
    @Schema(description = "优惠券 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long couponId;
}

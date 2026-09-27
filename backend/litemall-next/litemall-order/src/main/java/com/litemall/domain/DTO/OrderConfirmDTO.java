package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "订单确认页DTO")
public class OrderConfirmDTO {

    @NotEmpty(message = "购物车项不能为空")
    @Schema(description = "购物车项ID列表")
    private List<Long> cartIds;

    @Schema(description = "收货地址ID")
    private Long addressId;

    @Schema(description = "优惠券ID")
    private Long couponId;
}

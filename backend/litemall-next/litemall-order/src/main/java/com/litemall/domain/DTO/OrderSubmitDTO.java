package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "提交订单DTO")
public class OrderSubmitDTO {

    @NotEmpty(message = "购物车项不能为空")
    @Schema(description = "购物车项ID列表")
    private List<Long> cartIds;

    @NotNull(message = "收货地址ID不能为空")
    @Schema(description = "收货地址ID")
    private Long addressId;

    @Schema(description = "优惠券ID（用户券记录ID，可选）")
    private Long couponId;

    @NotNull(message = "幂等token不能为空")
    @Schema(description = "一次性幂等token，由 /app/order/idem-token 申请")
    private String idemToken;

    @Schema(description = "订单留言")
    private String message;
}

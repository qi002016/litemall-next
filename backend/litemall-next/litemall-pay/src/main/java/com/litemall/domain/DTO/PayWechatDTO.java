package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * <p>
 * 微信支付下单DTO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "微信支付下单")
public class PayWechatDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", type = "string")
    private Long orderId;
}

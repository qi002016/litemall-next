package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * <p>
 * 申请退款DTO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "申请退款")
public class RefundDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0.01", message = "退款金额必须大于0")
    @Schema(description = "退款金额")
    private BigDecimal amount;

    @NotBlank(message = "退款原因不能为空")
    @Schema(description = "退款原因")
    private String reason;
}

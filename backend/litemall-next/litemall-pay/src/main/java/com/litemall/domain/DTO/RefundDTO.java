package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * <p>
 * 申请退款DTO（CI-105，仅演示直付单可用）
 * </p>
 * <p>
 * 金额口径：对外整数分（06 §4，元↔分只在 DTO 边界转换），故此处为 Integer 而非 BigDecimal。
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
    @Min(value = 1, message = "退款金额必须大于0")
    @Schema(description = "退款金额（整数分）")
    private Integer amount;

    @NotBlank(message = "退款原因不能为空")
    @Schema(description = "退款原因")
    private String reason;
}
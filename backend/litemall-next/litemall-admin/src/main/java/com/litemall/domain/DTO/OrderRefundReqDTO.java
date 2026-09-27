package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * M13：管理端退款处理请求（原为 {@code Map<String, Object>}）。
 * <p>
 * {@code refundAmount} 保持可空（业务语义是"为空则默认实付金额"），只加了非负校验——
 * 负数退款金额此前是能传到 order 服务的。
 */
@Data
@Schema(description = "管理端退款处理请求")
public class OrderRefundReqDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @DecimalMin(value = "0", message = "退款金额不能为负")
    @Schema(description = "退款金额（可空，默认实付金额）")
    private BigDecimal refundAmount;

    @Size(max = 255, message = "退款备注长度不能超过 255 位")
    @Schema(description = "退款备注（可空）")
    private String refundContent;
}
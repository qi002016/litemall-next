package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端发起退款请求（CN-07 {@code POST /admin/aftersale/refund}）。
 * <p>
 * {@code amount}（整数分）可空：传了即须等于售后单金额，不等由 order 侧返回 400
 * {@code REFUND_AMOUNT_MISMATCH}（admin 不做金额计算比对，避免读写不一致时误判）。
 * </p>
 */
@Data
@Schema(description = "管理端售后退款请求")
public class AftersaleRefundReqDTO {

    @NotNull(message = "售后单ID不能为空")
    @Schema(description = "售后单ID（须为待收货 waitReceive 或退款中 refunding）")
    private Long aftersaleId;

    @Min(value = 1, message = "退款金额必须 ≥1 分")
    @Schema(description = "退款金额（整数分，可空；传了须等于售后单金额）", type = "integer")
    private Integer amount;

    @Size(max = 255, message = "退款备注长度不能超过 255 位")
    @Schema(description = "退款备注（可空，≤255）")
    private String remark;
}

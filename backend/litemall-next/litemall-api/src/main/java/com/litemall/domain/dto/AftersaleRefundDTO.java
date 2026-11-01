package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端发起退款入参（CN-07 / CN-08）。{@code amount} 传则必须等于售后单金额（分）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后退款入参")
public class AftersaleRefundDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "退款金额（整数分，可选，仅一致性校验）")
    private Integer amount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "操作人 admin.id", type = "string")
    private Long adminId;
}

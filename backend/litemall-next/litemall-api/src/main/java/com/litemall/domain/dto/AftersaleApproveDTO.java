package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 管理端售后审核通过入参（CN-03）。{@code returnAddress} 只覆盖本次响应，不落库（07 §4.8）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后审核通过入参")
public class AftersaleApproveDTO {

    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "退货地址（可选，覆盖全局配置，仅本次响应生效）")
    private String returnAddress;

    @Schema(description = "审核备注")
    private String remark;

    @Schema(description = "操作人 admin.id（对内显式透传）", type = "string")
    private Long adminId;
}

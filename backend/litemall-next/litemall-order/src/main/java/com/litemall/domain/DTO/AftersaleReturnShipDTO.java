package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户填写退货物流（CN-18 / 07 §4.4）。
 */
@Data
@Schema(description = "售后寄回物流DTO")
public class AftersaleReturnShipDTO {

    @NotNull(message = "售后单ID不能为空")
    @Schema(description = "售后单ID", type = "string")
    private Long aftersaleId;

    @NotBlank(message = "物流公司不能为空")
    @Size(max = 64, message = "物流公司不能超过 64 字")
    @Schema(description = "退货物流公司")
    private String returnShipCompany;

    @NotBlank(message = "物流单号不能为空")
    @Size(max = 64, message = "物流单号不能超过 64 字")
    @Schema(description = "退货物流单号")
    private String returnShipSn;
}

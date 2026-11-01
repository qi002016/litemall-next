package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端售后审核通过请求（CN-03 {@code POST /admin/aftersale/approve}）。
 * <p>
 * P-23：{@code returnAddress} 仅对 {@code type=2}（退货退款）有意义，且<b>只覆盖本次响应的地址、
 * 不落库</b>；缺省时由 order 侧读全局配置 {@code hm.aftersale.returnAddress}，两者都空 ⇒ 400。
 * admin 不读取该配置，只做长度校验并透传。
 * </p>
 */
@Data
@Schema(description = "管理端售后审核通过请求")
public class AftersaleApproveReqDTO {

    @NotNull(message = "售后单ID不能为空")
    @Schema(description = "售后单ID（须为申请中 applied）")
    private Long aftersaleId;

    @Size(max = 255, message = "退货地址长度不能超过 255 位")
    @Schema(description = "退货地址（仅 type=2 有效，≤255；缺省取全局配置）")
    private String returnAddress;

    @Size(max = 255, message = "审核备注长度不能超过 255 位")
    @Schema(description = "审核备注（可空，≤255）")
    private String remark;
}

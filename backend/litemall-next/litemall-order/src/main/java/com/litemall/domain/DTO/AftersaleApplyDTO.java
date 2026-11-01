package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 申请售后入参（CI-91 / 07 §4.1）。
 * <p>
 * 按订单行申请：{@code orderGoodsId} 必填，{@code number} 为该行本次申请件数；
 * {@code amount} 由服务端按行实付计算，前端传值只做一致性校验（不一致 → 400 REFUND_AMOUNT_MISMATCH）。
 * </p>
 */
@Data
@Schema(description = "申请售后DTO")
public class AftersaleApplyDTO {

    @NotNull(message = "订单行ID不能为空")
    @Schema(description = "订单行ID（litemall_order_goods.id）", type = "string")
    private Long orderGoodsId;

    @Schema(description = "订单ID（可选，传则必须与该行所属订单一致）", type = "string")
    private Long orderId;

    @NotNull(message = "售后类型不能为空")
    @Schema(description = "售后类型：1 仅退款 / 2 退货退款")
    private Integer type;

    @NotNull(message = "申请数量不能为空")
    @Schema(description = "申请退款数量")
    private Integer number;

    @NotBlank(message = "售后原因不能为空")
    @Size(max = 255, message = "售后原因不能超过 255 字")
    @Schema(description = "售后原因")
    private String reason;

    @Size(max = 255, message = "补充说明不能超过 255 字")
    @Schema(description = "补充说明")
    private String comment;

    @Schema(description = "凭证图片数组（退货退款必填 1~6 张）")
    private List<String> images;

    @Schema(description = "退款金额（整数分，可选，仅一致性校验）")
    private Integer amount;
}
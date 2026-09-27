package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * <p>
 * 管理端退款处理请求 DTO（H5：admin 不再直写 litemall_order，改经 order 服务内状态机，
 * 并由 order 服务完成库存回补与优惠券回退）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端退款处理请求")
public class OrderRefundDTO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    /** 为空时由 order 服务按订单实付金额兜底 */
    @Schema(description = "退款金额（可空，默认实付金额）")
    private BigDecimal refundAmount;

    @Schema(description = "退款备注（可空）")
    private String refundContent;
}
package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 支付结果VO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "支付结果")
public class PayResultVO {

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    /** unpaid / paid / refunded */
    @Schema(description = "支付状态：unpaid / paid / refunded")
    private String status;

    @Schema(description = "支付时间；未支付为 null")
    private String payTime;

    @Schema(description = "微信支付单号；未支付为 null")
    private String payId;
}

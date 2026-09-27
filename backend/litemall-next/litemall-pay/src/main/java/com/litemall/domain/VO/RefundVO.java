package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 退款申请结果VO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "退款申请结果")
public class RefundVO {

    @Schema(description = "退款单ID", type = "string")
    private Long refundId;

    @Schema(description = "状态：processing（退款中）")
    private String status;
}

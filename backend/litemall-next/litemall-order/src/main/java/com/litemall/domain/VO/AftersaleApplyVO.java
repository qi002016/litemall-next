package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 申请售后结果VO（4.11）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "申请售后结果VO")
public class AftersaleApplyVO {

    @Schema(description = "售后申请单ID", type = "string")
    private Long aftersaleId;

    @Schema(description = "状态：pending（待审核）")
    private String status;
}

package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 意见反馈结果VO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "意见反馈结果")
public class FeedbackVO {

    @Schema(description = "反馈记录ID", type = "string")
    private Long feedbackId;
}

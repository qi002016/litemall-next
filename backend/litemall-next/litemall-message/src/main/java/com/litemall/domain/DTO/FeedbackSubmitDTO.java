package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 意见反馈提交DTO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "意见反馈提交")
public class FeedbackSubmitDTO {

    @NotBlank(message = "反馈内容不能为空")
    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "联系方式")
    private String mobile;

    @Schema(description = "截图")
    private List<String> pictures;
}

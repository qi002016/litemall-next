package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 发表商品评论 DTO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
@Schema(description = "发表商品评论DTO")
public class CommentDTO {

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    @Schema(description = "评论内容")
    @NotBlank(message = "评论内容不能为空")
    private String content;

    @Schema(description = "评分 1-5")
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分范围为1-5")
    @Max(value = 5, message = "评分范围为1-5")
    private Integer star;

    @Schema(description = "评论图片")
    private List<String> picUrls;
}

package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 * 商品评论 VO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
@Schema(description = "商品评论VO")
public class CommentVO {

    @Schema(description = "评论ID", type = "string")
    private Long id;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "评分 1-5")
    private Integer star;

    @Schema(description = "评论图片")
    private String[] picUrls;

    @Schema(description = "评论时间")
    private LocalDateTime addTime;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "用户头像")
    private String avatar;
}

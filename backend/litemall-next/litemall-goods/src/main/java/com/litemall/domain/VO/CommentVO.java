package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 商品评论 VO（08 v4 CI-35 {@code GET /app/goods/comment/list}）
 * </p>
 *
 * <p>
 * R1 只做<b>读</b>路径：只返回 {@code status=0}（显示中）的评论，total 也只计未隐藏；
 * 新增商家回复字段（{@code adminContent/replyTime}，D-11）与 {@code hasPicture}。
 * 评价提交（CI-36）与后台评价管理（CN-29~31）归 R2。
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

    @Schema(description = "是否含图")
    private Boolean hasPicture;

    @Schema(description = "评论时间 yyyy-MM-dd HH:mm:ss")
    private String addTime;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "用户头像")
    private String avatar;

    @Schema(description = "商家回复内容")
    private String adminContent;

    @Schema(description = "商家回复时间 yyyy-MM-dd HH:mm:ss")
    private String replyTime;
}
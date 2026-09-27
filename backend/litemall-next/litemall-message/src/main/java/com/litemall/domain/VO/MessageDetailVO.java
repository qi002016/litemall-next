package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 消息详情VO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "消息详情")
public class MessageDetailVO {

    @Schema(description = "用户消息记录ID", type = "string")
    private Long id;

    @Schema(description = "消息类型：order / system / coupon")
    private String type;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "完整内容（支持富文本）")
    private String content;

    @Schema(description = "是否已读")
    private Boolean readStatus;

    @Schema(description = "接收时间")
    private String addTime;
}

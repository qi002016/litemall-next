package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 消息列表VO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "消息列表项")
public class MessageVO {

    @Schema(description = "用户消息记录ID", type = "string")
    private Long id;

    @Schema(description = "消息模板ID", type = "string")
    private Long msgId;

    @Schema(description = "消息类型：order / system / coupon")
    private String type;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "摘要（前50字）")
    private String brief;

    @Schema(description = "是否已读")
    private Boolean readStatus;

    @Schema(description = "接收时间")
    private String addTime;
}

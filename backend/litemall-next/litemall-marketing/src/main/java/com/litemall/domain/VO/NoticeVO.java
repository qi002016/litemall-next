package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通知公告VO")
public class NoticeVO {

    @Schema(description = "公告ID", type = "string")
    private Long id;

    @Schema(description = "公告标题")
    private String title;

    @Schema(description = "公告内容")
    private String content;

    @Schema(description = "发布时间（yyyy-MM-dd HH:mm:ss）")
    private String addTime;
}

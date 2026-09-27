package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema
public class AdVO {

    @Schema(description = "广告主ID", type = "string")
    private Long id;

    @Schema(description = "广告标题")
    private String name;

    @Schema(description = "广告图片")
    private String url;

    @Schema(description = "广告链接")
    private String link;

    @Schema(description = "广告位置")
    private String position;

    @Schema(description = "广告内容")
    private String content;
}

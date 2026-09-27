package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "专题VO")
public class TopicVO {

    @Schema(description = "专题ID", type = "string")
    private Long id;

    @Schema(description = "专题标题")
    private String title;

    @Schema(description = "子标题")
    private String subtitle;

    @Schema(description = "专题封面图")
    private String picUrl;

    @Schema(description = "专题相关商品最低价")
    private BigDecimal price;

    @Schema(description = "阅读量（如 1.2k）")
    private String readCount;
}

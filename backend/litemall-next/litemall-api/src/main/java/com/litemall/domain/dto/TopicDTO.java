package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 管理端专题 DTO（H5：admin 不再直写 litemall_topic，改经 marketing 服务）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端专题信息")
public class TopicDTO {

    @Schema(description = "专题ID")
    private Integer id;

    @Schema(description = "专题标题")
    private String title;

    @Schema(description = "专题子标题")
    private String subtitle;

    @Schema(description = "专题内容（富文本）")
    private String content;

    @Schema(description = "专题相关商品最低价")
    private BigDecimal price;

    @Schema(description = "阅读量，如 1.2k")
    private String readCount;

    @Schema(description = "专题封面图")
    private String picUrl;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}
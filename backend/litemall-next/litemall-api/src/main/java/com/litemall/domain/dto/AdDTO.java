package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 * 管理端广告 DTO（H5：admin 不再直写 litemall_ad，改经 marketing 服务）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端广告信息")
public class AdDTO {

    @Schema(description = "广告ID")
    private Long id;

    @Schema(description = "广告标题")
    private String name;

    @Schema(description = "跳转链接")
    private String link;

    @Schema(description = "广告图片")
    private String url;

    @Schema(description = "广告位置：1 首页")
    private Integer position;

    @Schema(description = "活动内容")
    private String content;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}
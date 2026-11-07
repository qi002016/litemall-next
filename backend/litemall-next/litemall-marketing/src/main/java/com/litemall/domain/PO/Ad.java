package com.litemall.domain.PO;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.time.LocalDateTime;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 广告表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_ad")
@Schema(description = "广告表")
public class Ad implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "广告标题")
    private String name;

    @Schema(description = "所广告的商品页面或者活动页面链接地址")
    private String link;

    @Schema(description = "广告宣传图片")
    private String url;

    @Schema(description = "广告位置：1则是首页")
    private Integer position;

    @Schema(description = "活动内容")
    private String content;

    @Schema(description = "广告开始时间")
    private LocalDateTime startTime;

    @Schema(description = "广告结束时间")
    private LocalDateTime endTime;

    @Schema(description = "是否启动")
    private Boolean enabled;

    @Schema(description = "排序值，越小越前（R3-02；列由迁移 SQL 人工执行后才有）")
    private Integer sortOrder;

    @Schema(description = "跳转类型：0 无跳转 / 1 商品 / 2 专题（BR-CMS-02）")
    private Integer linkType;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;


}

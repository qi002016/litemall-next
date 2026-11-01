package com.litemall.domain.PO;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 专题表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_topic")
@Schema(description = "专题表")
public class Topic implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "专题标题")
    private String title;

    @Schema(description = "专题子标题")
    private String subtitle;

    @Schema(description = "专题内容，富文本格式")
    private String content;

    @Schema(description = "专题相关商品最低价")
    private BigDecimal price;

    @Schema(description = "专题阅读量，如 1.2k")
    private String readCount;

    @Schema(description = "专题封面图")
    private String picUrl;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "是否启用：1 启用 / 0 停用（需求 5.12 专题启停）")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}

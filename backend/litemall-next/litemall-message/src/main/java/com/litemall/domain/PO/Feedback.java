package com.litemall.domain.PO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 意见反馈表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_feedback")
@Schema(description = "意见反馈表")
public class Feedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户表的用户ID")
    private Long userId;

    @Schema(description = "用户名称")
    private String username;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "反馈类型")
    private String feedType;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "是否含有图片")
    private Boolean hasPicture;

    @Schema(description = "图片地址列表，JSON数组格式")
    private String picUrls;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}

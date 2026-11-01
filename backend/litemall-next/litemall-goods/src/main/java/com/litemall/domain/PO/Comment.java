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
 * 评论表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_comment")
@Schema(description="评论表")
public class Comment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "评论的商品或专题ID")
    private Long valueId;

    @Schema(description = "评论类型，如果type=0，则是商品评论；如果是type=1，则是专题评论；")
    private Integer type;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "管理员回复内容")
    private String adminContent;

    @Schema(description = "用户表的用户ID")
    private Long userId;

    @Schema(description = "是否含有图片")
    private Boolean hasPicture;

    @Schema(description = "图片地址列表，采用JSON数组格式")
    private String picUrls;

    @Schema(description = "评分， 1-5")
    private Integer star;

    @Schema(description = "来源订单（BR-CM-01）")
    private Long orderId;

    @Schema(description = "来源订单行，唯一（BR-CM-01）")
    private Long orderGoodsId;

    @Schema(description = "商家回复时间（BR-CM-03）")
    private LocalDateTime replyTime;

    @Schema(description = "评价状态：0 显示 / 1 已隐藏（BR-CM-04）")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;


}

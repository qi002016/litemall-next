package com.litemall.domain.PO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单商品表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_order_goods")
@Schema(description = "订单商品表")
public class OrderGoods implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "订单ID")
    private Integer orderId;

    @Schema(description = "商品ID")
    private Integer goodsId;

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "商品编号")
    private String goodsSn;

    @Schema(description = "商品货品ID")
    private Integer productId;

    @Schema(description = "商品货品的购买数量")
    private Integer number;

    @Schema(description = "商品货品的售价")
    private BigDecimal price;

    @Schema(description = "商品货品的规格列表，JSON数组")
    private String specifications;

    @Schema(description = "商品货品图片或者商品图片")
    private String picUrl;

    @Schema(description = "订单商品评论：-1 超期不能评价；0 可以评价；>0 评论ID")
    private Integer comment;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}

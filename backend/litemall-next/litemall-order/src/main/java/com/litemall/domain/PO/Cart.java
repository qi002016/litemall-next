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
 * 购物车商品表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_cart")
@Schema(description = "购物车商品表")
public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "商品ID")
    private Long goodsId;

    @Schema(description = "商品编号")
    private String goodsSn;

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "商品货品ID（规格）")
    private Long productId;

    @Schema(description = "单价")
    private BigDecimal price;

    @Schema(description = "数量")
    private Integer number;

    @Schema(description = "规格值数组，JSON")
    private String specifications;

    @Schema(description = "是否选中")
    private Boolean checked;

    @Schema(description = "商品图")
    private String picUrl;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}

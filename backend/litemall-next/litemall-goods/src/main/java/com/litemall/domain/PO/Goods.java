package com.litemall.domain.PO;

import java.math.BigDecimal;
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
 * 商品基本信息表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_goods")
@Schema(description="商品基本信息表")
public class Goods implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "商品编号")
    private String goodsSn;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "商品所属类目ID")
    private Long categoryId;

    private Long brandId;

    @Schema(description = "商品宣传图片列表，采用JSON数组格式")
    private String gallery;

    @Schema(description = "商品关键字，采用逗号间隔")
    private String keywords;

    @Schema(description = "商品简介")
    private String brief;

    @Schema(description = "是否上架")
    private Boolean isOnSale;

    private Integer sortOrder;

    @Schema(description = "商品页面商品图片")
    private String picUrl;

    @Schema(description = "商品分享海报")
    private String shareUrl;

    @Schema(description = "是否新品首发，如果设置则可以在新品首发页面展示")
    private Boolean isNew;

    @Schema(description = "是否人气推荐，如果设置则可以在人气推荐页面展示")
    private Boolean isHot;

    @Schema(description = "商品单位，例如件、盒")
    private String unit;

    @Schema(description = "专柜价格")
    private BigDecimal counterPrice;

    @Schema(description = "零售价格")
    private BigDecimal retailPrice;

    @Schema(description = "商品详细介绍，是富文本格式")
    private String detail;

    @Schema(description = "归属商户，单商户期固定 1（BR-ADM-06）")
    private Long merchantId;

    @Schema(description = "商品状态：0 草稿 / 1 在售 / 2 已下架（需求 5.1；与 isOnSale 双向映射）")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;


}

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
 * 商品货品表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_goods_product")
@Schema(description="商品货品表")
public class GoodsProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "商品表的商品ID")
    private Long goodsId;

    @Schema(description = "商品规格值列表，采用JSON数组格式")
    private String specifications;

    @Schema(description = "商品货品价格")
    private BigDecimal price;

    @Schema(description = "商品货品数量")
    private Integer number;

    @Schema(description = "商品货品图片")
    private String url;

    @Schema(description = "SKU 稳定业务码（BR-GOODS-07），格式 goodsId-<specValueId 升序用_连接>")
    private String skuCode;

    @Schema(description = "规格值 ID 升序逗号串（BR-GOODS-02），形如 1,3")
    private String specValueIds;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;


}

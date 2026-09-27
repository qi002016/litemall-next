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
 * 商品参数表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_goods_attribute")
@Schema(description="商品参数表")
public class GoodsAttribute implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "商品表的商品ID")
    private Integer goodsId;

    @Schema(description = "商品参数名称")
    private String attribute;

    @Schema(description = "商品参数值")
    private String value;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;


}

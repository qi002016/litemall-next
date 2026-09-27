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
 * 团购规则表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_groupon_rules")
@Schema(description = "团购规则表")
public class GrouponRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "商品ID")
    private Long goodsId;

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "商品图片")
    private String picUrl;

    @Schema(description = "团购优惠金额（直减）")
    private BigDecimal discount;

    @Schema(description = "成团人数")
    private Integer discountMember;

    @Schema(description = "团购过期时间")
    private LocalDateTime expireTime;

    /**
     * 0 正常上线；1 到期自动下线；2 管理手动下线
     */
    @Schema(description = "0 正常上线；1 到期自动下线；2 手动下线")
    private Short status;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}

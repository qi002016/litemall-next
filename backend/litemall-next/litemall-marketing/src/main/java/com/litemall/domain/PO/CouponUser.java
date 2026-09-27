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
 * 优惠券用户使用表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_coupon_user")
@Schema(description = "优惠券用户使用表")
public class CouponUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "优惠券ID")
    private Long couponId;

    /**
     * 使用状态：0 未使用；1 已使用；2 已过期；3 已下架
     */
    @Schema(description = "使用状态：0 未使用；1 已使用；2 已过期；3 已下架")
    private Short status;

    @Schema(description = "使用时间")
    private LocalDateTime usedTime;

    @Schema(description = "有效期开始时间")
    private LocalDateTime startTime;

    @Schema(description = "有效期截至时间")
    private LocalDateTime endTime;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}

package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 管理端优惠券 DTO（H5：admin 不再直写 litemall_coupon，改经 marketing 服务）
 * </p>
 * <p>
 * 字段集与 marketing 服务 {@code Coupon} 实体一致（含 goodsType/goodsValue/code/timeType/days），
 * 因此管理端列表响应与写请求的 JSON 字段名保持不变。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端优惠券信息")
public class CouponDTO {

    @Schema(description = "优惠券ID")
    private Long id;

    @Schema(description = "优惠券名称")
    private String name;

    @Schema(description = "优惠券介绍")
    private String desc;

    @Schema(description = "优惠券标签")
    private String tag;

    @Schema(description = "优惠券数量，0 表示不限量")
    private Integer total;

    @Schema(description = "优惠金额")
    private BigDecimal discount;

    @Schema(description = "最少消费金额")
    private BigDecimal min;

    @Schema(description = "用户领券限制数量，0 表示不限")
    private Integer limit;

    @Schema(description = "赠送类型：0 通用券，1 注册赠券，2 兑换码")
    private Integer type;

    @Schema(description = "状态：0 正常，1 过期，2 下架")
    private Integer status;

    @Schema(description = "商品限制类型：0 全商品，1 类目，2 商品")
    private Integer goodsType;

    @Schema(description = "商品限制值")
    private String goodsValue;

    @Schema(description = "优惠券兑换码")
    private String code;

    @Schema(description = "有效时间限制：0 领取后 days 天，1 固定起止时间")
    private Integer timeType;

    @Schema(description = "领取后有效天数")
    private Integer days;

    @Schema(description = "使用券开始时间")
    private LocalDateTime startTime;

    @Schema(description = "使用券截止时间")
    private LocalDateTime endTime;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}
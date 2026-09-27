package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 管理端订单列表行 DTO（admin 服务经 Feign 从 order 服务获取，H5）
 * </p>
 * <p>
 * 字段与改造前 {@code admin/controller/OrderController#list} 返回的 Map 保持一一对应，
 * 因此管理端前端契约不变。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端订单列表行")
public class OrderAdminDTO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单编号")
    private String orderSn;

    @Schema(description = "用户ID")
    private Long userId;

    /** 改造前取 {@code orderPrice}，前端列名 orderAmount */
    @Schema(description = "订单金额")
    private BigDecimal orderAmount;

    /** 文本状态：unpaid/undelivered/shipped/confirmed/refund/canceled/unknown */
    @Schema(description = "订单状态文本")
    private String status;

    @Schema(description = "下单时间")
    private LocalDateTime addTime;
}
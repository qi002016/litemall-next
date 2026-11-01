package com.litemall.domain.DTO;

import lombok.Data;

/**
 * 订单号只读投影（{@code litemall_order} 跨域只读，见 {@code OrderSnRefMapper}）。
 *
 * @since 2026-11-01
 */
@Data
public class OrderSnDTO {

    /** 订单ID */
    private Long id;

    /** 订单号 */
    private String orderSn;
}
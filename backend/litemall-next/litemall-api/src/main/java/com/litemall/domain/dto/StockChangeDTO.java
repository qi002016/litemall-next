package com.litemall.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量库存变更请求（M2）。
 * <p>
 * 原实现按购物车项/订单商品逐条调用 Feign 扣减/回补库存，下单 RT 随件数线性增长；
 * 本 DTO 让一次 RPC 携带全部变更项，由商品服务在一次调用内完成。
 * </p>
 *
 * <p>
 * R1（03 §4.7 D-10）只新增**可选**字段，缺省即旧行为：{@code orderId}、{@code items[].orderGoodsId}
 * 用于调用方串联幂等键与审计；商品域不据此做幂等判断（回补幂等在 order 侧 {@code restock_status} CAS）。
 * </p>
 */
@Data
public class StockChangeDTO {

    private List<Item> items;

    /** 关联订单ID（可选，仅透传审计用） */
    private Long orderId;

    /** 单个库存变更项 */
    @Data
    public static class Item {
        private Long productId;
        private Integer number;

        /** 关联订单商品ID（可选，售后退款回补时携带；商品域不做幂等判断） */
        private Long orderGoodsId;
    }
}
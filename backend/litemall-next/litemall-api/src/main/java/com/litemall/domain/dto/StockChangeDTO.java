package com.litemall.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量库存变更请求（M2）。
 * <p>
 * 原实现按购物车项/订单商品逐条调用 Feign 扣减/回补库存，下单 RT 随件数线性增长；
 * 本 DTO 让一次 RPC 携带全部变更项，由商品服务在一次调用内完成。
 */
@Data
public class StockChangeDTO {

    private List<Item> items;

    /** 单个库存变更项 */
    @Data
    public static class Item {
        private Long productId;
        private Integer number;
    }
}
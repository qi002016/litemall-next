package com.litemall.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量回补库存结果（03 §4.7 D-24）。
 *
 * <p>
 * 售后退款回补语义：重复回补**不报错**，由 {@code released=false} 表达「本次未实际回补」；
 * 真正的幂等判断在 order 侧 {@code restock_status} CAS，商品域只如实回填影响行。
 * </p>
 */
@Data
public class ReleasedBatchResultDTO {

    /** 整批是否全部实际回补 */
    private boolean released;

    /** 逐项结果，顺序与请求 items 一致 */
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long productId;

        /** 该项是否实际回补（货品不存在或影响行数为 0 时为 false，不报错） */
        private boolean released;
    }

    public static ReleasedBatchResultDTO of(List<Item> items) {
        ReleasedBatchResultDTO dto = new ReleasedBatchResultDTO();
        dto.setItems(items);
        dto.setReleased(items != null && !items.isEmpty() && items.stream().allMatch(Item::isReleased));
        return dto;
    }
}
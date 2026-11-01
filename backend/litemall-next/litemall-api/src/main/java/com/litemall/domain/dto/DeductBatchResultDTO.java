package com.litemall.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量扣减库存结果（03 §4.7 D-24 / P-31）。
 *
 * <p>
 * 语义：整批要么全部扣减成功（{@code deducted=true}），要么整批回滚（{@code deducted=false}），
 * 失败时由 {@code items[].shortage} 指出缺口数量，供 order 侧填充 {@code shortage[]}
 * 并保持订单未付款（101）状态。**不**用异常/409 表达库存不足，避免 pay 侧重试造成二次扣减。
 * </p>
 */
@Data
public class DeductBatchResultDTO {

    /** 整批是否扣减成功 */
    private boolean deducted;

    /** 逐项结果，顺序与请求 items 一致 */
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long productId;

        /** 该项是否扣减成功（整批失败时恒为 false） */
        private boolean deducted;

        /** 缺口数量（= 需求量 − 可扣数量）；该项成功时为 null */
        private Integer shortage;
    }

    /**
     * 构造整批成功结果。
     */
    public static DeductBatchResultDTO success(List<Item> items) {
        DeductBatchResultDTO dto = new DeductBatchResultDTO();
        dto.setDeducted(true);
        dto.setItems(items);
        return dto;
    }

    /**
     * 构造整批失败结果（已整批回滚）。
     *
     * @param items 每项 deducted=false，失败项带 shortage
     */
    public static DeductBatchResultDTO failure(List<Item> items) {
        DeductBatchResultDTO dto = new DeductBatchResultDTO();
        dto.setDeducted(false);
        dto.setItems(items);
        return dto;
    }
}
package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * CN-IN-04 出参：各 SKU 的锁定量（{@code /inner/order/locked-stock}）。
 * <p>
 * 语义：{@code lockedQty} = 未付款（101）且未删除的订单行 number 之和；
 * goods 侧用它算 {@code sellable = max(0, number - lockedQty)}，<b>只能读不能写</b>，
 * 且不得据此「先算可售再扣减」（快照语义，07 §6.7）。
 * </p>
 */
@Data
@Schema(description = "SKU 锁定量列表")
public class LockedStockDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "锁定量列表，按 productId 升序；无占用为空数组")
    private List<Item> list = new ArrayList<>();

    @Data
    @Schema(description = "单个 SKU 的锁定量")
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        /** productId 为 BIGINT，全局 Long→String 序列化后出参是字符串 */
        @Schema(description = "货品/SKU ID", type = "string")
        private Long productId;

        @Schema(description = "锁定量（未付款订单占用件数）")
        private Integer lockedQty;
    }
}
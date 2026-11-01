package com.litemall.domain.VO;

import com.litemall.domain.dto.StockAdminDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 库存调整结果（08 v4 CN-14 出参：{@code productId, number, locked, sellable, lowStock, updateTime}）。
 * </p>
 * <p>
 * 只暴露契约声明的 6 个字段；{@code locked}/{@code sellable} 在锁量不可用时为 null
 * （D-07b：{@code lockedUnavailable=true} 的降级口径）。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "库存调整结果")
public class StockUpdateVO {

    @Schema(description = "货品（SKU）ID", type = "string")
    private Long productId;

    @Schema(description = "调整后库存数量", type = "integer")
    private Integer number;

    @Schema(description = "锁定数量（锁量不可用时为 null）", type = "integer")
    private Integer locked;

    @Schema(description = "可售数量（锁量不可用时为 null）", type = "integer")
    private Integer sellable;

    @Schema(description = "是否低库存（sellable ≤ 5）")
    private Boolean lowStock;

    @Schema(description = "更新时间 yyyy-MM-dd HH:mm:ss")
    private String updateTime;

    /**
     * 由 goods 侧返回值转换；返回值为空时退化为请求值。
     *
     * @param source    goods 侧返回的库存行（可空）
     * @param productId 请求的货品ID
     * @param number    请求的库存数量
     * @return 契约出参
     */
    public static StockUpdateVO of(StockAdminDTO source, Long productId, Integer number) {
        StockUpdateVO vo = new StockUpdateVO();
        vo.setProductId(source != null && source.getProductId() != null ? source.getProductId() : productId);
        vo.setNumber(source != null && source.getNumber() != null ? source.getNumber() : number);
        if (source != null) {
            vo.setLocked(source.getLocked());
            vo.setSellable(source.getSellable());
            vo.setLowStock(source.getLowStock());
            vo.setUpdateTime(source.getUpdateTime());
        }
        return vo;
    }
}
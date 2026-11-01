package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端库存调整请求（08 v4 CN-14 {@code PUT /admin/stock/update}）。
 * <p>
 * {@code number} 是<b>绝对值</b>（不是增量）；{@code number < locked} 由 goods 侧返回 409
 * {@code STOCK_ADJUST_CONFLICT}；{@code expectNumber} 为期望原值，用于并发校验。
 * </p>
 */
@Data
@Schema(description = "管理端库存调整请求")
public class StockUpdateReqDTO {

    @NotNull(message = "货品ID不能为空")
    @Schema(description = "货品（SKU）ID")
    private Long productId;

    @NotNull(message = "库存数量不能为空")
    @Min(value = 0, message = "库存数量不能为负")
    @Schema(description = "库存数量（绝对值，≥0）")
    private Integer number;

    @Min(value = 0, message = "期望库存不能为负")
    @Schema(description = "期望原库存（并发校验用，可空）")
    private Integer expectNumber;

    @Size(max = 100, message = "备注长度不能超过 100 位")
    @Schema(description = "调整备注（≤100，写入操作日志）")
    private String remark;
}
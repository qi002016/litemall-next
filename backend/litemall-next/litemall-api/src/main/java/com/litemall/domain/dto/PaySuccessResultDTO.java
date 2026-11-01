package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * CI-97 支付成功回调出参（07 §6.3 + 03 §4.6 P-31）。
 * <p>
 * <b>不抛业务异常、不返回 409</b>：实扣失败也返回 200，用 {@code deducted=false} 表达，
 * 订单保持 101（不置 102、不自动退款），并写异常日志 + 告警。
 * </p>
 */
@Data
@Schema(description = "支付成功回调结果")
public class PaySuccessResultDTO {

    @Schema(description = "本次回调是否真正推进了订单（重复回调为 false）")
    private boolean applied;

    @Schema(description = "库存是否实扣成功（applied=false 时为 false）")
    private boolean deducted;

    @Schema(description = "库存缺口明细（成功为空数组）")
    private List<Shortage> shortage = new ArrayList<>();

    @Data
    @Schema(description = "库存缺口")
    public static class Shortage {

        @Schema(description = "货品/SKU ID", type = "string")
        private Long productId;

        @Schema(description = "需要件数")
        private Integer need;

        @Schema(description = "可扣件数（实际库存）")
        private Integer available;
    }
}
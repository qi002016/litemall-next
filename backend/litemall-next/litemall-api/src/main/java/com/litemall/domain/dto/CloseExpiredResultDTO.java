package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * CN-11 超时关单结果（{@code /inner/order/admin/close-expired}，07 §3.5）。
 * <p>
 * 逐单 CAS（{@code status=101 AND expire_time<=now} → 103 + close_type=2），
 * 因此并发被用户取消的订单会以 {@code ok=false} 出现在 {@code items} 里；可重复执行、不返回 404。
 * </p>
 */
@Data
@Schema(description = "超时关单结果")
public class CloseExpiredResultDTO {

    @Schema(description = "本轮扫描到的过期未付款订单数")
    private int total;

    @Schema(description = "成功关闭数")
    private int succeeded;

    @Schema(description = "失败数")
    private int failed;

    @Schema(description = "逐单结果")
    private List<Item> items = new ArrayList<>();

    @Data
    @Schema(description = "单笔关单结果")
    public static class Item {

        @Schema(description = "订单ID", type = "string")
        private Long id;

        @Schema(description = "是否关闭成功")
        private boolean ok;

        @Schema(description = "失败原因（成功为 null）")
        private String reason;
    }
}
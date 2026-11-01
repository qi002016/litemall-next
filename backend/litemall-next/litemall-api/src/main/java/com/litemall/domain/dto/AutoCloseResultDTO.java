package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 待寄回超时自动关闭出参（CN-IN-03）。无命中时 {@code closed=0}，不返回 404。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "售后超时自动关闭出参")
public class AutoCloseResultDTO {

    @Schema(description = "成功关闭数量")
    private Integer closed = 0;

    @Schema(description = "失败明细")
    private List<Item> failed = new ArrayList<>();

    /** 失败明细：id + 原因 */
    @Data
    @Schema(description = "自动关闭失败明细")
    public static class Item {

        @Schema(description = "售后单ID", type = "string")
        private Long id;

        @Schema(description = "失败原因")
        private String reason;
    }
}

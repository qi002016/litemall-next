package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 库存管理 DTO（08 v4 CN-13 / CN-14，goods 对内接口，admin 经 Feign 消费）
 * </p>
 *
 * <p>
 * 一份 DTO 同时承担三种角色，字段按「入参优先、出参补充」合并；调用方按需取用：
 * </p>
 * <ul>
 *   <li><b>调整入参</b>（CN-14）：{@code productId} / {@code number} / {@code remark} / {@code expectNumber}；</li>
 *   <li><b>列表行</b>（CN-13）：{@code skuCode … updateTime} 全部字段；</li>
 *   <li><b>调整出参</b>（CN-14）：{@code productId / number / locked / sellable / lowStock / updateTime}。</li>
 * </ul>
 *
 * <p>
 * 三量口径（08 §2.1）：{@code number} 为唯一持久化库存；{@code locked} 由 order 域 CN-IN-04 派生
 * （未支付订单占用）；{@code sellable = max(0, number − locked)}。
 * order 不可达时 {@code locked}/{@code sellable} 为 {@code null} 且 {@code lockedUnavailable = true}，
 * <b>不得</b>把未知当成 0。
 * </p>
 */
@Data
@Schema(description = "库存行 / 库存调整入参与出参")
public class StockAdminDTO {

    /* ==================== 标识 ==================== */

    @Schema(description = "货品（SKU）ID")
    private Long productId;

    @Schema(description = "SKU 业务码，如 215-3_7")
    private String skuCode;

    @Schema(description = "商品ID")
    private Long goodsId;

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "商品状态：0 草稿 / 1 在售 / 2 已下架", type = "integer")
    private Integer goodsStatus;

    @Schema(description = "商品状态文案")
    private String goodsStatusText;

    @Schema(description = "规格文本，如「白色 / 350ml」")
    private String specText;

    @Schema(description = "商品主图")
    private String picUrl;

    /* ==================== 三量 ==================== */

    @Schema(description = "实际库存（调整入参为绝对值，≥0）", type = "integer")
    private Integer number;

    @Schema(description = "锁定数量（未支付订单占用，来自 order CN-IN-04；不可用时为 null）", type = "integer")
    private Integer locked;

    @Schema(description = "可售数量 = max(0, number − locked)；locked 不可用时为 null", type = "integer")
    private Integer sellable;

    @Schema(description = "是否低库存（sellable ≤ lowStockThreshold）")
    private Boolean lowStock;

    @Schema(description = "低库存阈值（恒 5）", type = "integer")
    private Integer lowStockThreshold;

    @Schema(description = "locked 是否不可用（order 域调用失败时为 true，此时 locked/sellable 为 null）")
    private Boolean lockedUnavailable;

    @Schema(description = "更新时间 yyyy-MM-dd HH:mm:ss")
    private String updateTime;

    /* ==================== 调整专用入参 ==================== */

    @Schema(description = "调整备注（≤100 字）")
    private String remark;

    @Schema(description = "期望的当前库存（可选；与实际不一致 ⇒ 409 CONCURRENT_MODIFIED）", type = "integer")
    private Integer expectNumber;
}
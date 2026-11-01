package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 订单域报表数据源（CI-100：{@code GET /inner/order/admin/stats}，admin 工作台 CI-08 的订单侧数据）。
 * </p>
 * <p>
 * R2 契约（05-R2 实施口径 §2.2，冻结，<b>字段只增不改</b>）：
 * </p>
 * <ul>
 *   <li>新增可选入参 {@code period}（{@code today|7d|30d}，默认 {@code 30d}），无参调用等价 30d；</li>
 *   <li>{@code todayOrders}/{@code undelivered} 保留且语义不变；{@code todayOrders} 口径为
 *       「今日<b>支付成功</b>订单数」（{@code pay_time} 落今日、{@code order_status NOT IN (101,103)}）；</li>
 *   <li>金额字段（{@code gmv}/{@code refundAmount}/{@code top5.amount}/{@code trend.gmv}）为
 *       <b>整数分</b>；计数为 {@code Long}（对内裸类型允许 Long，对外由 admin 转字符串）；</li>
 *   <li><b>不含</b> {@code avgOrderValue}/{@code refundRate}/{@code unavailable}/{@code logErrorCount}/
 *       {@code goodsTotal}/{@code userTotal}：派生与拼装在 admin（§2.1）。</li>
 * </ul>
 *
 * @author qihuang
 * @since 2026-09-27
 */
@Data
@Schema(description = "管理端订单统计（CI-100，订单域报表数据源）")
public class OrderAdminStatsDTO {

    // ------------------------- R1 沿用（语义不变） -------------------------

    @Schema(description = "今日支付成功订单数（恒为今日，不随 period 变化）")
    private Long todayOrders;

    @Schema(description = "待发货订单数（order_status=102 存量，不随 period 变化）")
    private Long undelivered;

    // ------------------------- R2 新增（CI-100） -------------------------

    @Schema(description = "生效统计期：today/7d/30d", example = "7d")
    private String period;

    @Schema(description = "期初（yyyy-MM-dd 00:00:00）", example = "2026-10-26 00:00:00")
    private String periodStart;

    @Schema(description = "期末当日（yyyy-MM-dd 23:59:59）", example = "2026-11-01 23:59:59")
    private String periodEnd;

    @Schema(description = "期内支付成功订单实付合计（整数分，不扣退款）", example = "128800")
    private BigDecimal gmv;

    @Schema(description = "期内支付成功订单数（不含未付款 101 / 已关闭 103）", example = "12")
    private Long orderCount;

    @Schema(description = "期内退款完成售后单数（COUNT DISTINCT aftersale_id）", example = "1")
    private Long refundCount;

    @Schema(description = "期内退款完成金额合计（整数分）", example = "12900")
    private BigDecimal refundAmount;

    @Schema(description = "当前待发货存量（order_status=102，与 undelivered 同值）", example = "2")
    private Long waitShipCount;

    @Schema(description = "当前待处理售后存量（status ∈ 0,1,2,3）", example = "1")
    private Long pendingAftersaleCount;

    @Schema(description = "热销 Top5（按支付订单商品件数降序，并列按金额降序；不足 5 条返回几条，无数据为空数组）")
    private List<TopGoods> top5;

    @Schema(description = "自然日趋势（长度固定 1/7/30，无单日期补 0）")
    private List<Trend> trend;

    /** 热销商品行 */
    @Data
    @Schema(description = "热销商品行")
    public static class TopGoods {

        @Schema(description = "商品ID（字符串化）", example = "1005002")
        private Long goodsId;

        @Schema(description = "商品名（组内最新）", example = "素物 马克杯")
        private String goodsName;

        @Schema(description = "期内支付订单的商品件数合计", example = "5")
        private Long quantity;

        @Schema(description = "期内金额合计（整数分）", example = "19900")
        private BigDecimal amount;
    }

    /** 自然日趋势行 */
    @Data
    @Schema(description = "自然日趋势行")
    public static class Trend {

        @Schema(description = "自然日（yyyy-MM-dd）", example = "2026-10-26")
        private String date;

        @Schema(description = "当日支付成功订单数", example = "1")
        private Long orderCount;

        @Schema(description = "当日 GMV（整数分）", example = "9900")
        private BigDecimal gmv;
    }
}
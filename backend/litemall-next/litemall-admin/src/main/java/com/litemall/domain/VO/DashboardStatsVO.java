package com.litemall.domain.VO;

import com.litemall.domain.dto.OrderAdminStatsDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * CI-08 工作台报表响应（05-R2 实施口径 §2.1.2 冻结）。
 * </p>
 * <p>
 * 口径要点：
 * </p>
 * <ul>
 *   <li><b>「五个数字」</b> = {@code gmv}、{@code orderCount}、{@code avgOrderValue}、{@code refundRate}、
 *       {@code refundAmount}（{@code refundCount} 作附属计数同源展示）——验收第 6/7 步的判定对象；</li>
 *   <li>金额一律<b>整数分</b>（{@code BigDecimal} 且 {@code scale=0}，JSON 为 number；不能声明为 {@code Long}，
 *       否则被全局 {@code Long→String} 序列化器转成字符串，与契约不符）；</li>
 *   <li>计数为 {@code Long}，全局序列化器统一转字符串（契约要求 string(Long)）；</li>
 *   <li>{@code avgOrderValue}: {@code gmv ÷ orderCount} <b>向下取整到分</b>，{@code orderCount=0} ⇒
 *       {@code null}（前端显示 {@code —}，<b>不显示 0/NaN</b>）；</li>
 *   <li>{@code refundRate}: {@code refundCount ÷ orderCount} 保留 4 位小数（{@code HALF_UP}），分母 0 ⇒
 *       {@code null}；</li>
 *   <li>{@code unavailable}: 单指标失败白名单（{@code gmv}/{@code top5}/{@code trend}/{@code refundAmount}/
 *       {@code waitShipCount}/{@code pendingAftersaleCount}），失败项对应字段 {@code null}，整体仍 200；</li>
 *   <li>{@code todayOrders}/{@code undelivered} 沿用 R1 字段名，语义见 §2.1.2。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端工作台报表（CI-08）")
public class DashboardStatsVO {

    @Schema(description = "生效统计期：today/7d/30d", example = "7d")
    private String period;

    @Schema(description = "期初（yyyy-MM-dd 00:00:00）", example = "2026-10-26 00:00:00")
    private String periodStart;

    @Schema(description = "期末当日（yyyy-MM-dd 23:59:59）", example = "2026-11-01 23:59:59")
    private String periodEnd;

    /** 五个数字之一：期内支付成功订单实付合计（整数分，不扣退款） */
    @Schema(description = "GMV（整数分，期内支付成功订单实付合计，不扣退款）", example = "128800")
    private BigDecimal gmv;

    /** 五个数字之一：期内支付成功订单数 */
    @Schema(description = "期内支付成功订单数（不含未付款 101 / 已关闭 103）", example = "12")
    private Long orderCount;

    /** 五个数字之一：客单价（整数分，向下取整；orderCount=0 ⇒ null） */
    @Schema(description = "客单价（整数分，gmv÷orderCount 向下取整；orderCount=0 ⇒ null）", example = "10733")
    private BigDecimal avgOrderValue;

    /** 五个数字之一：退款率（4 位小数；分母 0 ⇒ null） */
    @Schema(description = "退款率（refundCount÷orderCount，4 位小数 HALF_UP；分母 0 ⇒ null）", example = "0.0833")
    private BigDecimal refundRate;

    /** 五个数字之一：期内退款完成金额合计（整数分） */
    @Schema(description = "退款金额（整数分，期内退款完成合计）", example = "12900")
    private BigDecimal refundAmount;

    @Schema(description = "期内退款完成售后单数（COUNT DISTINCT aftersale_id）", example = "1")
    private Long refundCount;

    @Schema(description = "商品总数")
    private Long goodsTotal;

    @Schema(description = "用户总数")
    private Long userTotal;

    @Schema(description = "今日支付成功订单数（恒为今日，不随 period 变化）")
    private Long todayOrders;

    @Schema(description = "待发货订单数（当前 order_status=102 存量，不随 period 变化）")
    private Long undelivered;

    @Schema(description = "当前待发货存量（与 undelivered 同值，08 明确字段）")
    private Long waitShipCount;

    @Schema(description = "当前待处理售后存量（status ∈ 0,1,2,3）")
    private Long pendingAftersaleCount;

    @Schema(description = "今日日志写入失败计数（Redis admin:logerr:{yyyyMMdd}）")
    private Long logErrorCount;

    @Schema(description = "热销 Top5（按支付订单商品件数降序，并列按金额降序；无数据空数组）")
    private List<OrderAdminStatsDTO.TopGoods> top5;

    @Schema(description = "自然日趋势（长度固定 1/7/30，无单日期补 0）")
    private List<OrderAdminStatsDTO.Trend> trend;

    @Schema(description = "不可用指标名白名单（失败项字段为 null，整体仍 200）")
    private List<String> unavailable;
}
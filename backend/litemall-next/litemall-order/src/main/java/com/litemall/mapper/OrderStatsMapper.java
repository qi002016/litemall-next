package com.litemall.mapper;

import com.litemall.domain.DTO.OrderStatsAggregates;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 订单域报表只读 Mapper（CI-100，05-R2 实施口径 §2.1.3 SQL-1..SQL-5）。
 * </p>
 * <p>
 * <b>SQL 逐字冻结</b>：本文件的 SQL 与实施口径 §2.1.3 完全一致，admin 侧 CI-08 的「五个数字」
 * 必须能被同一份 SQL 手算复现（验收第 6/7 步）。改动前须先改口径卡。
 * </p>
 * <p>
 * 只读：本 Mapper 不含任何写语句，不违反 §1.7「一表只有唯一写服务」的红线；
 * {@code litemall_refund} 由 pay 域唯一写入，此处沿用 R1 先例只读（§5.18）。
 * </p>
 *
 * @since 2026-11-01
 */
@Mapper
public interface OrderStatsMapper {

    /**
     * SQL-1：期内 GMV + 支付成功订单数（归期 = {@code pay_time}）。
     *
     * @param start 期初 {@code 00:00:00}（含）
     * @param end   次日 {@code 00:00:00}（不含）
     * @return 聚合行（{@code COUNT(*)} 恒有值）
     */
    @Select("""
            SELECT COUNT(*)                         AS orderCount,
                   COALESCE(SUM(o.actual_price), 0) AS gmvYuan
              FROM litemall_order o
             WHERE o.deleted = 0
               AND o.order_status NOT IN (101, 103)
               AND o.pay_time >= #{start} AND o.pay_time < #{end}
            """)
    OrderStatsAggregates.Period selectPeriodAgg(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    /**
     * SQL-2：期内退款完成数（{@code COUNT(DISTINCT aftersale_id)}）+ 金额（归期 = {@code refund_time}）。
     *
     * @param start 期初 {@code 00:00:00}（含）
     * @param end   次日 {@code 00:00:00}（不含）
     * @return 聚合行（{@code COUNT(DISTINCT …)} 恒有值；{@code aftersale_id IS NULL} 的历史行不计入）
     */
    @Select("""
            SELECT COUNT(DISTINCT r.aftersale_id) AS refundCount,
                   COALESCE(SUM(r.amount), 0)     AS refundYuan
              FROM litemall_refund r
             WHERE r.deleted = 0
               AND r.status = 1
               AND r.refund_time >= #{start} AND r.refund_time < #{end}
            """)
    OrderStatsAggregates.Refund selectRefundAgg(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    /**
     * SQL-3：热销 Top5（按支付订单商品件数降序，并列按金额降序、商品ID升序兜底）。
     *
     * @param start 期初 {@code 00:00:00}（含）
     * @param end   次日 {@code 00:00:00}（不含）
     * @return 至多 5 行；无数据返回空列表
     */
    @Select("""
            SELECT og.goods_id            AS goodsId,
                   MAX(og.goods_name)     AS goodsName,
                   SUM(og.number)         AS quantity,
                   SUM(og.actual_amount)  AS amountYuan
              FROM litemall_order_goods og
              JOIN litemall_order o ON o.id = og.order_id AND o.deleted = 0
             WHERE og.deleted = 0
               AND o.order_status NOT IN (101, 103)
               AND o.pay_time >= #{start} AND o.pay_time < #{end}
             GROUP BY og.goods_id
             ORDER BY quantity DESC, amountYuan DESC, goodsId ASC
             LIMIT 5
            """)
    List<OrderStatsAggregates.GoodsSales> selectTopGoods(@Param("start") LocalDateTime start,
                                                         @Param("end") LocalDateTime end);

    /**
     * SQL-4：自然日趋势（只返回有支付的日期；缺失日期由 service 补 0）。
     *
     * @param start 期初 {@code 00:00:00}（含）
     * @param end   次日 {@code 00:00:00}（不含）
     * @return 命中日期的聚合行
     */
    @Select("""
            SELECT DATE(o.pay_time)                 AS date,
                   COUNT(*)                         AS orderCount,
                   COALESCE(SUM(o.actual_price), 0) AS gmvYuan
              FROM litemall_order o
             WHERE o.deleted = 0
               AND o.order_status NOT IN (101, 103)
               AND o.pay_time >= #{start} AND o.pay_time < #{end}
             GROUP BY DATE(o.pay_time)
            """)
    List<OrderStatsAggregates.TrendPoint> selectTrend(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);

    /**
     * SQL-5（其一）：当前待发货存量。
     *
     * @return {@code order_status=102 AND deleted=0} 的订单数
     */
    @Select("SELECT COUNT(*) FROM litemall_order WHERE deleted = 0 AND order_status = 102")
    Long countWaitShip();

    /**
     * SQL-5（其二）：当前待处理售后存量。
     *
     * @return {@code status IN (0,1,2,3) AND deleted=0} 的售后单数
     */
    @Select("SELECT COUNT(*) FROM litemall_aftersale WHERE deleted = 0 AND status IN (0, 1, 2, 3)")
    Long countPendingAftersale();
}
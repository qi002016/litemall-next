package com.litemall.domain.DTO;

import lombok.Data;

import java.math.BigDecimal;

/**
 * <p>
 * 订单域报表聚合行（CI-100 / 05-R2 实施口径 §2.1.3 SQL-1..SQL-4 的查询载体）。
 * </p>
 * <p>
 * 只做「SQL 结果 → Java」的映射，不含业务语义：金额字段一律以<b>元</b>
 * （{@code DECIMAL(10,2)}）承接，转「分」在 service 边界统一
 * {@code movePointRight(2).setScale(0, HALF_UP)} 完成（§5.5）。
 * </p>
 *
 * @since 2026-11-01
 */
public final class OrderStatsAggregates {

    private OrderStatsAggregates() {
    }

    /** SQL-1：期内 GMV 与支付成功订单数 */
    @Data
    public static class Period {

        /** 期内支付成功订单数（{@code COUNT(*)}） */
        private Long orderCount;

        /** 期内支付成功订单实付合计（元） */
        private BigDecimal gmvYuan;
    }

    /** SQL-2：期内退款完成数与金额 */
    @Data
    public static class Refund {

        /** 期内退款完成售后单数（{@code COUNT(DISTINCT r.aftersale_id)}） */
        private Long refundCount;

        /** 期内退款完成金额合计（元） */
        private BigDecimal refundYuan;
    }

    /** SQL-3：热销 Top5 行 */
    @Data
    public static class GoodsSales {

        private Long goodsId;

        private String goodsName;

        /** 期内支付订单的商品件数合计 */
        private Long quantity;

        /** 期内金额合计（元） */
        private BigDecimal amountYuan;
    }

    /** SQL-4：自然日趋势行（只含命中日期，补 0 在 service 侧） */
    @Data
    public static class TrendPoint {

        /** 自然日 {@code yyyy-MM-dd} */
        private String date;

        /** 当日支付成功订单数 */
        private Long orderCount;

        /** 当日 GMV（元） */
        private BigDecimal gmvYuan;
    }
}
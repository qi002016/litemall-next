package com.litemall.support;

import com.litemall.domain.PO.OrderGoods;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单域纯规则单测（D9 券额分摊 / fullyRefunded / refundedAmount / hasUnreviewed / 501 判定）。
 * <p>口径来源：03 Q5、07 §2.2 DR-08。</p>
 */
class OrderRulesTest {

    /* ==================== D9 券额分摊 ==================== */

    @Test
    @DisplayName("D9：按行金额占比分摊，尾差入最后一行，Σ 与券额分毫不差")
    void allocateCoupon_tailGoesToLastLine() {
        // 100 / 50 / 50，券 33.33 → 16.67 / 8.33 / 8.33（尾差 0.00 恰好齐平需构造非整除场景）
        List<BigDecimal> shares = OrderRules.allocateCoupon(
                List.of(new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("50.00")),
                new BigDecimal("33.34"));

        assertThat(shares).hasSize(3);
        assertThat(shares.get(0)).isEqualByComparingTo("16.67");
        assertThat(shares.get(1)).isEqualByComparingTo("8.34");
        assertThat(shares.get(2)).isEqualByComparingTo("8.33");
        assertThat(sum(shares)).isEqualByComparingTo("33.34");
    }

    @Test
    @DisplayName("D9：三行均分除不尽时，最后一行吃掉全部尾差")
    void allocateCoupon_repeatingTail() {
        List<BigDecimal> shares = OrderRules.allocateCoupon(
                List.of(new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("10.00")),
                new BigDecimal("10.00"));

        assertThat(shares.get(0)).isEqualByComparingTo("3.33");
        assertThat(shares.get(1)).isEqualByComparingTo("3.33");
        assertThat(shares.get(2)).isEqualByComparingTo("3.34");
        assertThat(sum(shares)).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("D9：券额 0 / null 时全部为 0")
    void allocateCoupon_zeroCoupon() {
        List<BigDecimal> zero = OrderRules.allocateCoupon(
                List.of(new BigDecimal("9.90"), new BigDecimal("1.00")), BigDecimal.ZERO);
        assertThat(zero).allSatisfy(s -> assertThat(s).isEqualByComparingTo("0"));

        List<BigDecimal> nullCoupon = OrderRules.allocateCoupon(
                List.of(new BigDecimal("9.90")), null);
        assertThat(nullCoupon).singleElement().satisfies(s -> assertThat(s).isEqualByComparingTo("0"));
    }

    @Test
    @DisplayName("D9：券额等于商品总额时，各行分摊等于行金额（订单金额 0）")
    void allocateCoupon_fullDeduction() {
        List<BigDecimal> lines = List.of(new BigDecimal("12.30"), new BigDecimal("7.70"));
        List<BigDecimal> shares = OrderRules.allocateCoupon(lines, new BigDecimal("20.00"));

        assertThat(shares.get(0)).isEqualByComparingTo("12.30");
        assertThat(shares.get(1)).isEqualByComparingTo("7.70");
    }

    @Test
    @DisplayName("D9：单行分摊不得超过该行金额")
    void allocateCoupon_neverExceedsLine() {
        List<BigDecimal> shares = OrderRules.allocateCoupon(
                List.of(new BigDecimal("0.01"), new BigDecimal("100.00")), new BigDecimal("100.00"));

        assertThat(shares.get(0)).isLessThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(sum(shares)).isEqualByComparingTo("100.00");
    }

    /* ==================== fullyRefunded / refundedAmount ==================== */

    @Test
    @DisplayName("fullyRefunded：所有行 refund_status=2 才为真")
    void fullyRefunded_requiresAllLinesRefunded() {
        assertThat(OrderRules.isFullyRefunded(List.of(line(1, 0, "10.00"), line(1, 2, "20.00")))).isFalse();
        assertThat(OrderRules.isFullyRefunded(List.of(line(1, 2, "10.00"), line(1, 2, "20.00")))).isTrue();
        assertThat(OrderRules.isFullyRefunded(List.of())).isTrue();
    }

    @Test
    @DisplayName("refundedAmount：Σ(actual_amount × refunded_number / number)，逐行四舍五入")
    void refundedAmount_proportional() {
        OrderGoods partial = new OrderGoods()
                .setNumber(3).setRefundedNumber(2).setActualAmount(new BigDecimal("30.00")).setRefundStatus(1);
        OrderGoods full = new OrderGoods()
                .setNumber(1).setRefundedNumber(1).setActualAmount(new BigDecimal("9.99")).setRefundStatus(2);

        assertThat(OrderRules.refundedAmount(List.of(partial, full))).isEqualByComparingTo("29.99");
    }

    @Test
    @DisplayName("refundedAmount：未退款行为 0，且不使用 order.refund_amount")
    void refundedAmount_ignoresUntouchedLines() {
        OrderGoods untouched = new OrderGoods()
                .setNumber(2).setRefundedNumber(0).setActualAmount(new BigDecimal("20.00")).setRefundStatus(0);

        assertThat(OrderRules.refundedAmount(List.of(untouched))).isEqualByComparingTo("0");
    }

    /* ==================== hasUnreviewed / 501 ==================== */

    @Test
    @DisplayName("hasUnreviewed：refund_status=0 且 comment=0/null 才算未评价；comment=-1 不参与")
    void hasUnreviewed_semantics() {
        assertThat(OrderRules.hasUnreviewed(List.of(comment(0, 0)))).isTrue();
        assertThat(OrderRules.hasUnreviewed(List.of(comment(0, null)))).isTrue();
        assertThat(OrderRules.hasUnreviewed(List.of(comment(0, -1)))).isFalse();
        assertThat(OrderRules.hasUnreviewed(List.of(comment(0, 7)))).isFalse();
        // 已部分退款的行即便未评价也不阻塞
        assertThat(OrderRules.hasUnreviewed(List.of(comment(1, 0)))).isFalse();
    }

    @Test
    @DisplayName("501：301 且非全额退款且无未评价行才可完成；R1 不可达")
    void canMarkCompleted_conditions() {
        OrderGoods reviewed = comment(0, 5);
        assertThat(OrderRules.canMarkCompleted(301, List.of(reviewed))).isTrue();
        assertThat(OrderRules.canMarkCompleted(301, List.of(comment(0, 0)))).isFalse();
        assertThat(OrderRules.canMarkCompleted(201, List.of(reviewed))).isFalse();
        assertThat(OrderRules.canMarkCompleted(301, List.of(line(1, 2, "10.00")))).isFalse();
        assertThat(OrderRules.canMarkCompleted(501, List.of(reviewed))).isFalse();
    }

    /* ==================== 辅助 ==================== */

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private OrderGoods line(int number, int refundStatus, String actual) {
        return new OrderGoods()
                .setNumber(number)
                .setRefundedNumber(0)
                .setRefundStatus(refundStatus)
                .setActualAmount(new BigDecimal(actual));
    }

    private OrderGoods comment(int refundStatus, Integer comment) {
        return new OrderGoods()
                .setNumber(1)
                .setRefundedNumber(0)
                .setRefundStatus(refundStatus)
                .setComment(comment)
                .setActualAmount(new BigDecimal("10.00"));
    }
}
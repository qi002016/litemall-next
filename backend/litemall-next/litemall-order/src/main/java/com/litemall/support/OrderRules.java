package com.litemall.support;

import com.litemall.domain.PO.OrderGoods;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 订单域纯规则（无 IO、无 Spring 依赖，便于单测）。
 * <p>
 * 口径来源：07 §2.2（DR-08 券额分摊）、03 实施口径 Q5（7 栏判定 / 501 唯一写点）、
 * 07 §1.2/§1.3（订单行退款状态）。
 * </p>
 */
public final class OrderRules {

    private OrderRules() {
    }

    /**
     * 券额按行商品金额占比分摊（DR-08 / D9）：
     * <ul>
     *   <li>{@code couponShare_i = round(coupon × lineAmount_i / goodsAmount, 2)}，四舍五入到分；</li>
     *   <li><b>尾差全部计入最后一行</b>，保证 {@code Σ couponShare == couponAmount}（分毫不差）；</li>
     *   <li>单行分摊额不超过该行商品金额（券不可能把某行摊成负数）。</li>
     * </ul>
     * 调用方保证 {@code 0 <= couponAmount <= goodsAmount}。
     *
     * @param lineAmounts 各行商品金额（元），顺序即落库顺序
     * @param couponAmount 券额（元）
     * @return 与 {@code lineAmounts} 等长的分摊额（元）
     */
    public static List<BigDecimal> allocateCoupon(List<BigDecimal> lineAmounts, BigDecimal couponAmount) {
        int size = lineAmounts.size();
        List<BigDecimal> shares = new ArrayList<>(size);
        if (size == 0) {
            return shares;
        }
        BigDecimal coupon = couponAmount == null ? BigDecimal.ZERO : couponAmount;
        BigDecimal goodsAmount = lineAmounts.stream()
                .map(a -> a == null ? BigDecimal.ZERO : a)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (coupon.signum() <= 0 || goodsAmount.signum() <= 0) {
            for (int i = 0; i < size; i++) {
                shares.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
            return shares;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < size - 1; i++) {
            BigDecimal line = lineAmounts.get(i) == null ? BigDecimal.ZERO : lineAmounts.get(i);
            BigDecimal share = coupon.multiply(line)
                    .divide(goodsAmount, 2, RoundingMode.HALF_UP);
            // 防御：单行分摊不得超过该行金额
            if (share.compareTo(line) > 0) {
                share = line.setScale(2, RoundingMode.HALF_UP);
            }
            shares.add(share);
            allocated = allocated.add(share);
        }
        // 尾差入最后一行
        BigDecimal last = coupon.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lastLine = lineAmounts.get(size - 1) == null ? BigDecimal.ZERO : lineAmounts.get(size - 1);
        if (last.signum() < 0) {
            last = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (last.compareTo(lastLine) > 0) {
            last = lastLine.setScale(2, RoundingMode.HALF_UP);
        }
        shares.add(last);
        return shares;
    }

    /**
     * fullyRefunded：{@code NOT EXISTS(og.deleted=0 AND refund_status<>2)}（03 Q5 原文）。
     * <p>入参须是已过滤 {@code deleted=0} 的订单行。</p>
     */
    public static boolean isFullyRefunded(List<OrderGoods> lines) {
        for (OrderGoods line : lines) {
            if (!Objects.equals(line.getRefundStatus(), 2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 已退款金额：{@code Σ(actual_amount × refunded_number / number)}，逐行四舍五入到分（03 Q5）。
     * <p>禁用 {@code order.refund_amount}（历史遗留字段，语义不一致）。</p>
     */
    public static BigDecimal refundedAmount(List<OrderGoods> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderGoods line : lines) {
            int number = line.getNumber() == null ? 0 : line.getNumber();
            int refunded = line.getRefundedNumber() == null ? 0 : line.getRefundedNumber();
            if (number <= 0 || refunded <= 0) {
                continue;
            }
            BigDecimal actual = line.getActualAmount() == null ? BigDecimal.ZERO : line.getActualAmount();
            sum = sum.add(actual.multiply(BigDecimal.valueOf(refunded))
                    .divide(BigDecimal.valueOf(number), 2, RoundingMode.HALF_UP));
        }
        return sum;
    }

    /**
     * 是否还有「未评价」行（isUnreviewed / hasUnreviewed，03 Q5）：
     * {@code refund_status=0 AND (comment IS NULL OR comment=0)}。
     * <p>{@code comment=-1}（不可评价）<b>不参与</b>「是否全部评价」判定；R1 不写 -1。</p>
     */
    public static boolean hasUnreviewed(List<OrderGoods> lines) {
        for (OrderGoods line : lines) {
            Integer refundStatus = line.getRefundStatus() == null ? 0 : line.getRefundStatus();
            Integer comment = line.getComment();
            boolean unreviewed = comment == null || comment == 0;
            if (refundStatus == 0 && unreviewed) {
                return true;
            }
        }
        return false;
    }

    /**
     * 501 已完成判定（03 Q5 冻结的唯一 CAS 条件，R1 不可达，仅供 R2 CI-36 与单测使用）：
     * {@code 当前 301} 且 {@code NOT fullyRefunded} 且 {@code 无「未退款且未评价」行}。
     */
    public static boolean canMarkCompleted(Integer currentStatus, List<OrderGoods> lines) {
        if (!Objects.equals(currentStatus, 301)) {
            return false;
        }
        return !isFullyRefunded(lines) && !hasUnreviewed(lines);
    }
}
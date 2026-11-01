package com.litemall.constant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 订单状态机常量与文案（R1 唯一一处定义，03 实施口径 Q5=丙 / 07 §1.1）。
 * <p>
 * <b>库内码只用于服务端状态机与 CAS 条件</b>；出参 {@code status} 始终是字符串标识
 * （{@code unpaid} 等），中文文案走 {@code statusText}。
 * </p>
 * <pre>
 * 101 unpaid         未付款      （下单初始态；锁定库存的唯一来源）
 * 102 undelivered    待发货      （支付成功唯一入口态）
 * 201 shipped        已发货
 * 301 confirmed      已收货
 * 501 completed      已完成      （R1 不可达：301→501 唯一写点在 R2 CI-36 评价成功后的 CAS）
 * 103 canceled       已关闭      （用户取消 / 超时关闭 / 运营关单，close_type 区分）
 * 401 refund         退款中      （存量只读，新流程停写）
 * 402 refunded       已退款      （存量只读，新流程停写）
 * </pre>
 */
public final class OrderStatus {

    public static final int UNPAID = 101;
    public static final int UNDELIVERED = 102;
    public static final int CANCELED = 103;
    public static final int SHIPPED = 201;
    public static final int CONFIRMED = 301;
    public static final int COMPLETED = 501;
    /** 存量只读：退款中 */
    public static final int REFUND = 401;
    /** 存量只读：已退款 */
    public static final int REFUND_DONE = 402;

    /* ==================== order.close_type（07 §1.3） ==================== */
    /** 1 用户取消 */
    public static final int CLOSE_USER = 1;
    /** 2 超时关闭 */
    public static final int CLOSE_TIMEOUT = 2;
    /** 3 运营关单 */
    public static final int CLOSE_ADMIN = 3;

    private OrderStatus() {
    }

    /**
     * 库内码 → 出参 {@code status} 字符串标识；未知码返回 {@code unknown}
     * （不抛异常：列表/详情遇到历史脏数据也要能返回）。
     */
    public static String ident(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case UNPAID -> "unpaid";
            case UNDELIVERED -> "undelivered";
            case SHIPPED -> "shipped";
            case CONFIRMED -> "confirmed";
            case COMPLETED -> "completed";
            case CANCELED -> "canceled";
            case REFUND -> "refund";
            case REFUND_DONE -> "refunded";
            default -> "unknown";
        };
    }

    /** 库内码 → 中文文案（03 Q5 唯一文案表） */
    public static String label(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case UNPAID -> "未付款";
            case UNDELIVERED -> "待发货";
            case SHIPPED -> "已发货";
            case CONFIRMED -> "已收货";
            case COMPLETED -> "已完成";
            case CANCELED -> "已关闭";
            case REFUND -> "退款中";
            case REFUND_DONE -> "已退款";
            default -> "未知状态";
        };
    }

    /**
     * 出参 {@code status} 标识 → 库内码；非法返回 {@code null}。
     * <p>
     * 必须覆盖 103/501/401/402：改造前只认 5 个值，导致后台按「已关闭」筛选时
     * 静默返回全部订单（03 §5.2 必修项）。
     * </p>
     */
    public static Integer parse(String ident) {
        if (ident == null) {
            return null;
        }
        return switch (ident.trim()) {
            case "unpaid" -> UNPAID;
            case "undelivered" -> UNDELIVERED;
            case "shipped" -> SHIPPED;
            case "confirmed" -> CONFIRMED;
            case "completed" -> COMPLETED;
            case "canceled" -> CANCELED;
            case "refund" -> REFUND;
            case "refunded" -> REFUND_DONE;
            default -> null;
        };
    }

    /** {@code status} 是否合法标识（用于 400 PARAM_INVALID 判定） */
    public static boolean isValidIdent(String ident) {
        return parse(ident) != null;
    }

    /**
     * 解析多值 {@code status}（逗号分隔，07 §3.1）；任一值非法 → 返回 {@code null}
     * 表示参数非法（调用方抛 400 PARAM_INVALID）。
     */
    public static List<Integer> parseMulti(String statuses) {
        if (statuses == null || statuses.isBlank()) {
            return List.of();
        }
        Set<Integer> codes = new LinkedHashSet<>();
        for (String raw : statuses.split(",")) {
            String item = raw.trim();
            if (item.isEmpty()) {
                return null;
            }
            Integer code = parse(item);
            if (code == null) {
                return null;
            }
            codes.add(code);
        }
        return new ArrayList<>(codes);
    }

    /** close_type → 中文文案（07 §1.3） */
    public static String closeTypeLabel(Integer closeType) {
        if (closeType == null) {
            return null;
        }
        return switch (closeType) {
            case CLOSE_USER -> "用户取消";
            case CLOSE_TIMEOUT -> "超时关闭";
            case CLOSE_ADMIN -> "运营关单";
            default -> "未知";
        };
    }
}
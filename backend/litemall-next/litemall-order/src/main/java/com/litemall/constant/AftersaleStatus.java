package com.litemall.constant;

/**
 * 售后状态/类型常量与文案（07 §1.2 / §1.3）。
 * <p>
 * R1 中订单域（CI-84 行级 canApply、CN-33 行级售后摘要、CN-20 未完结售后判定）只读这些状态；
 * 售后写流程（CN-01~09）归 R1-03，同一套常量，避免两处定义漂移。
 * </p>
 */
public final class AftersaleStatus {

    /** 0 申请中（非终态） */
    public static final int APPLIED = 0;
    /** 1 待寄回 */
    public static final int WAIT_RETURN = 1;
    /** 2 待收货 */
    public static final int WAIT_RECEIVE = 2;
    /** 3 退款中（唯一允许停留待重试的非终态） */
    public static final int REFUNDING = 3;
    /** 4 退款完成（终态） */
    public static final int COMPLETED = 4;
    /** 5 已关闭（终态） */
    public static final int CLOSED = 5;
    /** 6 已驳回（终态） */
    public static final int REJECTED = 6;
    /** 7 已撤销（终态） */
    public static final int CANCELLED = 7;

    /** 1 仅退款 */
    public static final int TYPE_REFUND_ONLY = 1;
    /** 2 退货退款 */
    public static final int TYPE_RETURN_GOODS = 2;
    /** 0 历史存量类型（只读，入参不接受） */
    public static final int TYPE_LEGACY = 0;

    /** 未完结状态：0/1/2/3（CN-20 不可隐藏、行级不可重复申请的唯一判定集合） */
    public static final int[] UNFINISHED = {APPLIED, WAIT_RETURN, WAIT_RECEIVE, REFUNDING};

    /** 可被运营关闭的状态：0/1/2（CN-09；退款中 3 与所有终态不可关闭） */
    public static final int[] CLOSABLE = {APPLIED, WAIT_RETURN, WAIT_RECEIVE};

    /** 库存回补：0 未回补 */
    public static final int RESTOCK_NONE = 0;
    /** 库存回补：1 已回补（含「无需回补」） */
    public static final int RESTOCK_DONE = 1;
    /** 库存回补：2 待回补（资金已退、回补失败；只能挂在退款中 3 上，CN-08 重试） */
    public static final int RESTOCK_PENDING = 2;

    private AftersaleStatus() {
    }

    /** 回补状态文案（0 未回补 / 1 已回补 / 2 待回补） */
    public static String restockLabel(Integer restockStatus) {
        if (restockStatus == null) {
            return "未回补";
        }
        return switch (restockStatus) {
            case RESTOCK_NONE -> "未回补";
            case RESTOCK_DONE -> "已回补";
            case RESTOCK_PENDING -> "待回补";
            default -> "未知回补状态";
        };
    }

    /** 是否终态（4/5/6/7） */
    public static boolean isTerminal(Integer status) {
        return status != null && status >= COMPLETED;
    }

    /**
     * 解析逗号分隔的 8 态标识（CN-15 / CN-01 入参）。
     * <p>空白返回空列表（不过滤）；任一段非法返回 {@code null}（调用方转 400 PARAM_INVALID）。</p>
     */
    public static java.util.List<Integer> parseMulti(String statuses) {
        java.util.List<Integer> codes = new java.util.ArrayList<>();
        if (statuses == null || statuses.trim().isEmpty()) {
            return codes;
        }
        for (String raw : statuses.split(",")) {
            String item = raw == null ? "" : raw.trim();
            if (item.isEmpty()) {
                return null;
            }
            Integer code = parse(item);
            if (code == null) {
                return null;
            }
            if (!codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    public static String label(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case APPLIED -> "申请中";
            case WAIT_RETURN -> "待寄回";
            case WAIT_RECEIVE -> "待收货";
            case REFUNDING -> "退款中";
            case COMPLETED -> "退款完成";
            case CLOSED -> "已关闭";
            case REJECTED -> "已驳回";
            case CANCELLED -> "已撤销";
            default -> "未知状态";
        };
    }

    /** 出参标识（与管理端筛选入参一致） */
    public static String ident(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case APPLIED -> "applied";
            case WAIT_RETURN -> "waitReturn";
            case WAIT_RECEIVE -> "waitReceive";
            case REFUNDING -> "refunding";
            case COMPLETED -> "completed";
            case CLOSED -> "closed";
            case REJECTED -> "rejected";
            case CANCELLED -> "cancelled";
            default -> "unknown";
        };
    }

    /** 出参标识 → 状态码；非法返回 null */
    /**
     * 入参解析：同时接受 8 个状态标识（applied/waitReturn/...）与数字 0..7，逗号多值时允许混用
     * （CN-01/CN-15 入参与 admin-web 传参口径对齐；非法返回 {@code null} 由调用方转 400）。
     */
    public static Integer parse(String ident) {
        if (ident == null) {
            return null;
        }
        return switch (ident.trim()) {
            case "0" -> APPLIED;
            case "1" -> WAIT_RETURN;
            case "2" -> WAIT_RECEIVE;
            case "3" -> REFUNDING;
            case "4" -> COMPLETED;
            case "5" -> CLOSED;
            case "6" -> REJECTED;
            case "7" -> CANCELLED;
            case "applied" -> APPLIED;
            case "waitReturn" -> WAIT_RETURN;
            case "waitReceive" -> WAIT_RECEIVE;
            case "refunding" -> REFUNDING;
            case "completed" -> COMPLETED;
            case "closed" -> CLOSED;
            case "rejected" -> REJECTED;
            case "cancelled" -> CANCELLED;
            default -> null;
        };
    }

    public static String typeLabel(Integer type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case TYPE_REFUND_ONLY -> "仅退款";
            case TYPE_RETURN_GOODS -> "退货退款";
            case TYPE_LEGACY -> "历史类型";
            default -> "未知类型";
        };
    }

    /** 行退款状态文案：0 未退款 / 1 部分退款 / 2 已全额退款 */
    public static String refundStatusLabel(Integer refundStatus) {
        if (refundStatus == null) {
            return "未退款";
        }
        return switch (refundStatus) {
            case 0 -> "未退款";
            case 1 -> "部分退款";
            case 2 -> "已全额退款";
            default -> "未知退款状态";
        };
    }
}
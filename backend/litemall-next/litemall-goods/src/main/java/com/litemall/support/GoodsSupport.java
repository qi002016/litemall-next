package com.litemall.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 商品域纯函数工具（无状态、无 IO，便于单测）。
 *
 * <p>
 * 集中三件容易写错的事：①金额「元 ↔ 整数分」边界转换；②{@code spec_value_ids} /
 * {@code sku_code} 的归一（升序、去重、缺省 {@code D}）；③三量口径（number / locked / sellable）。
 * </p>
 */
public final class GoodsSupport {

    /** 时间统一出参格式（08 全局口径） */
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 低库存阈值：08 冻结为恒 5，不做配置项 */
    public static final int LOW_STOCK_THRESHOLD = 5;

    /** 无规格 SKU 的 sku_code 占位段（BR-GOODS-07） */
    public static final String NO_SPEC_CODE = "D";

    /** 规格文本分隔符（08 示例「白 / 350ml」） */
    public static final String SPEC_TEXT_SEPARATOR = " / ";

    private GoodsSupport() {
    }

    /* ==================== 金额：元 ↔ 整数分 ==================== */

    /**
     * 库内「元」（DECIMAL）转为对外「整数分」。
     */
    public static Integer yuanToFen(BigDecimal yuan) {
        if (yuan == null) {
            return null;
        }
        return yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 对外「整数分」转为库内「元」（两位小数）。
     */
    public static BigDecimal fenToYuan(Integer fen) {
        if (fen == null) {
            return null;
        }
        return BigDecimal.valueOf(fen).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }

    /* ==================== 时间 ==================== */

    public static String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    public static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text.trim(), TIME_FORMATTER);
    }

    /* ==================== 规格值 ID ==================== */

    /**
     * 把 {@code " 3, 1 ,3 "} 这类库内串解析为升序去重的 ID 列表；空串 ⇒ 空列表。
     */
    public static List<String> parseSpecValueIds(String specValueIds) {
        List<String> ids = new ArrayList<>();
        if (specValueIds == null || specValueIds.isBlank()) {
            return ids;
        }
        for (String part : specValueIds.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        ids.sort(ID_ORDER);
        return ids;
    }

    /**
     * 归一为库内逗号串（升序去重）；空集合 ⇒ 空串。
     */
    public static String joinSpecValueIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                unique.add(id.trim());
            }
        }
        List<String> sorted = new ArrayList<>(unique);
        sorted.sort(ID_ORDER);
        return String.join(",", sorted);
    }

    /**
     * 生成 SKU 业务码：{@code goodsId-<specValueId 升序用 _ 连接>}；无规格 ⇒ {@code goodsId-D}。
     * <p>对应 BR-GOODS-07 与迁移脚本的回填口径，二者必须一致。</p>
     */
    public static String buildSkuCode(Long goodsId, List<String> ids) {
        String tail;
        if (ids == null || ids.isEmpty()) {
            tail = NO_SPEC_CODE;
        } else {
            List<String> sorted = new ArrayList<>(new LinkedHashSet<>(ids));
            sorted.removeIf(id -> id == null || id.isBlank());
            sorted.sort(ID_ORDER);
            tail = sorted.isEmpty() ? NO_SPEC_CODE : String.join("_", sorted);
        }
        return goodsId + "-" + tail;
    }

    /**
     * 规格文本：{@code ["白","350ml"]} ⇒ {@code "白 / 350ml"}；空 ⇒ 空串。
     */
    public static String buildSpecText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                kept.add(value.trim());
            }
        }
        return String.join(SPEC_TEXT_SEPARATOR, kept);
    }

    /* ==================== 三量：number / locked / sellable ==================== */

    /**
     * 可售 = max(0, number − locked)。
     *
     * @param lockedLocked locked 已知时传占用数；{@code lockedUnavailable=true} 时传 {@code null}
     * @return locked 不可用时返回 {@code null}（未知，不得当 0）
     */
    public static Integer sellable(Integer number, Integer locked, boolean lockedUnavailable) {
        if (lockedUnavailable) {
            return null;
        }
        int stock = number == null ? 0 : number;
        int lock = locked == null ? 0 : locked;
        return Math.max(0, stock - lock);
    }

    /**
     * 低库存判定：sellable ≤ 5；sellable 未知 ⇒ {@code false}（不误报）。
     */
    public static boolean lowStock(Integer sellable) {
        return sellable != null && sellable <= LOW_STOCK_THRESHOLD;
    }

    /**
     * 汇总可售量；任一项未知则整体未知（返回 null），避免把「不知道」算成 0。
     */
    public static Integer sumSellable(List<Integer> sellables, boolean lockedUnavailable) {
        if (lockedUnavailable) {
            return null;
        }
        int sum = 0;
        for (Integer value : sellables) {
            sum += value == null ? 0 : value;
        }
        return sum;
    }

    /* ==================== 状态文案 ==================== */

    /**
     * 商品状态文案：0 草稿 / 1 在售 / 2 已下架。
     */
    public static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "在售";
            case 2 -> "已下架";
            default -> "未知";
        };
    }

    /** 数值型 ID 升序比较（"10" 排在 "9" 之后） */
    private static final Comparator<String> ID_ORDER = (a, b) -> {
        try {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        } catch (NumberFormatException ignore) {
            return a.compareTo(b);
        }
    };
}
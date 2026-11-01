package com.litemall.support;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * 操作日志「目标类型」白名单与路径推导（CI-13 / §2.5.1 / §5.11）。
 * </p>
 * <p>
 * 三个职责：
 * </p>
 * <ol>
 *   <li>提供白名单常量（CI-13 入参校验与写日志共用同一份，避免读写出两套枚举）；</li>
 *   <li>把控制器的候选值（{@code OperationLogContext}）规范化：不在白名单内的<b>不落库</b>，
 *       否则 CI-13 永远筛不出来（§5.11「审计缺口」）；</li>
 *   <li>按请求路径兜底推导：写操作即便控制器没声明 targetType，也能落一个白名单值，
 *       满足 §2.5.3「target_type 必填」。</li>
 * </ol>
 * <p>
 * 兜底优先级：控制器显式声明 &gt; 路径前缀 &gt; {@code admin}（账号/日志/未知管理类动作）。
 * </p>
 *
 * @since 2026-11-01
 */
public final class LogTargetTypes {

    /** 白名单（§2.5.1 冻结字面量，10 项，小写） */
    public static final Set<String> WHITELIST = Set.of(
            "order", "goods", "sku", "aftersale", "coupon", "ad", "topic", "notice", "admin", "comment");

    /** 最终兜底：无法归类时归到「管理账号/管理动作」，保证 target_type 非空且在白名单内 */
    public static final String FALLBACK = "admin";

    /**
     * 路径前缀 → 白名单值。注意 admin 的控制器基路径，品牌/分类归 {@code goods}（白名单无 brand/category），
     * 用户管理归 {@code admin}（白名单无 user）。
     */
    private static final Map<String, String> PATH_PREFIXES = Map.ofEntries(
            Map.entry("/admin/order", "order"),
            Map.entry("/admin/aftersale", "aftersale"),
            Map.entry("/admin/comment", "comment"),
            Map.entry("/admin/notice", "notice"),
            Map.entry("/admin/coupon", "coupon"),
            Map.entry("/admin/market", "coupon"),
            Map.entry("/admin/ad", "ad"),
            Map.entry("/admin/topic", "topic"),
            Map.entry("/admin/stock", "sku"),
            Map.entry("/admin/goods", "goods"),
            Map.entry("/admin/brand", "goods"),
            Map.entry("/admin/category", "goods"),
            Map.entry("/admin/account", "admin"),
            Map.entry("/admin/user", "admin"),
            Map.entry("/admin/log", "admin"),
            Map.entry("/admin/auth", "admin"));

    private LogTargetTypes() {
    }

    /**
     * 规范化候选值：命中白名单（大小写不敏感）返回小写规范写法，否则 {@code null}。
     *
     * @param raw 候选值（通常来自控制器 {@code OperationLogContext}）
     * @return 白名单内的规范值，或 null
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return WHITELIST.contains(value) ? value : null;
    }

    /**
     * 解析本次写操作应落库的 {@code target_type}（保证非空且在白名单内）。
     *
     * @param path      请求路径（{@code request.getRequestURI()}）
     * @param candidate 控制器声明的候选值，可为 null
     * @return 白名单内的目标类型，永不为 null
     */
    public static String resolve(String path, String candidate) {
        String explicit = normalize(candidate);
        if (explicit != null) {
            return explicit;
        }
        if (path != null) {
            // 前缀必须落在路径段边界（/admin/ad 不能吃掉 /admin/admin/**），命中多个取最长
            String bestPrefix = null;
            String bestValue = null;
            for (Map.Entry<String, String> entry : PATH_PREFIXES.entrySet()) {
                String prefix = entry.getKey();
                boolean hit = path.equals(prefix) || path.startsWith(prefix + "/");
                if (hit && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                    bestPrefix = prefix;
                    bestValue = entry.getValue();
                }
            }
            if (bestValue != null) {
                return bestValue;
            }
        }
        return FALLBACK;
    }
}
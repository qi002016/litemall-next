package com.litemall.support;

import com.litemall.exception.BadRequestException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <p>
 * 管理端列表/写接口的公共入参归一与校验（纯函数，便于单测）。
 * </p>
 * <p>
 * R1 依据 06 §3.1 与 08 v4：
 * </p>
 * <ul>
 *   <li>分页对外参数名统一 {@code pageNum}（默认 1）；{@code pageSize} 默认 20、上限 100（超出按 100，不报错）；</li>
 *   <li>{@code sortBy} 为白名单枚举，<b>非法值忽略并落默认，不报 400</b>；</li>
 *   <li>状态类多值入参（{@code status}/{@code aftersaleStatus}/{@code goodsStatus}）为逗号分隔的
 *       <b>出参字符串标识</b>，非法 token ⇒ 400 {@code PARAM_INVALID}（06 §1.2.1）；</li>
 *   <li>时间筛选格式固定 {@code yyyy-MM-dd HH:mm:ss}，格式不符 ⇒ 400 {@code PARAM_INVALID}。</li>
 * </ul>
 * <p>
 * admin 只做「入参校验 + 转发 + 权限」，状态流转/金额/库存判定全部在对端服务
 * （03 §6.2「接口分界」）。
 * </p>
 *
 * @since 2026-11-01
 */
public final class AdminQuerySupport {

    /** 默认页码（06 §3.1） */
    public static final int DEFAULT_PAGE_NUM = 1;
    /** 默认每页条数（06 §3.1） */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /** 每页条数上限（06 §3.1：超出按 100，不报错） */
    public static final int MAX_PAGE_SIZE = 100;
    /** 关键字类入参最大长度（07/08 统一 ≤64 或 ≤50，调用方各自传上限） */
    public static final int DEFAULT_KEYWORD_MAX = 64;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AdminQuerySupport() {
    }

    /**
     * 页码归一：null 或 &lt;1 ⇒ 1。
     *
     * @param pageNum 原始页码
     * @return 归一后的页码
     */
    public static int pageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 每页条数归一：null 或 &lt;1 ⇒ 20；&gt;100 ⇒ 100（06 §3.1，不报错）。
     *
     * @param pageSize 原始每页条数
     * @return 归一后的每页条数
     */
    public static int pageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 排序字段白名单归一：空/非法 ⇒ 默认值（06 §3.1「非法值忽略并落默认，不报 400」）。
     *
     * @param sortBy   原始排序字段
     * @param allowed  白名单（大小写不敏感）
     * @param fallback 默认排序字段
     * @return 白名单内的规范写法，或 {@code fallback}
     */
    public static String sortBy(String sortBy, Set<String> allowed, String fallback) {
        String canonical = canonicalToken(sortBy == null ? null : sortBy.trim(), allowed);
        return canonical == null ? fallback : canonical;
    }

    /**
     * 逗号分隔的多值枚举归一：去空白、去重、按白名单规范化大小写；任一 token 非法 ⇒ 400。
     *
     * @param raw     原始入参，可空
     * @param allowed 白名单
     * @param field   字段名（用于错误提示）
     * @return 规范化后的逗号分隔串；入参为空时返回 {@code null}（表示不筛选）
     */
    public static String csvTokens(String raw, Set<String> allowed, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            String canonical = canonicalToken(token, allowed);
            if (canonical == null) {
                throw new BadRequestException(field + " 取值非法：" + token, "PARAM_INVALID");
            }
            tokens.add(canonical);
        }
        return tokens.isEmpty() ? null : String.join(",", tokens);
    }

    /**
     * 文本入参：去首尾空白，空串归 null；超长 ⇒ 400。
     *
     * @param raw    原始入参
     * @param maxLen 最大长度
     * @param field  字段名
     * @return 去空白后的文本，或 null
     */
    public static String text(String raw, int maxLen, String field) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLen) {
            throw new BadRequestException(field + " 长度不能超过 " + maxLen + " 位", "PARAM_INVALID");
        }
        return value;
    }

    /**
     * 文本入参（默认 64 字上限）。
     *
     * @param raw   原始入参
     * @param field 字段名
     * @return 去空白后的文本，或 null
     */
    public static String text(String raw, String field) {
        return text(raw, DEFAULT_KEYWORD_MAX, field);
    }

    /**
     * 时间入参：格式固定 {@code yyyy-MM-dd HH:mm:ss}，不符 ⇒ 400。
     *
     * @param raw   原始入参
     * @param field 字段名
     * @return 原始格式串，或 null
     */
    public static String dateTime(String raw, String field) {
        String value = text(raw, 32, field);
        if (value == null) {
            return null;
        }
        try {
            LocalDateTime.parse(value, DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " 格式应为 yyyy-MM-dd HH:mm:ss", "PARAM_INVALID");
        }
        return value;
    }

    /**
     * 布尔入参默认值。
     *
     * @param value        原始值
     * @param defaultValue 缺省值
     * @return 归一后的布尔值
     */
    public static boolean bool(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String canonicalToken(String token, Set<String> allowed) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        for (String candidate : allowed) {
            if (candidate.equalsIgnoreCase(token)) {
                return candidate;
            }
        }
        return null;
    }
}
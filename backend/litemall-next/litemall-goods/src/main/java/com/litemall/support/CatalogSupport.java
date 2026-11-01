package com.litemall.support;

import com.litemall.exception.GoodsApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * <p>
 * 分类/品牌（R2-01）入参校验与金额/时间换算的纯函数集合。
 * </p>
 * <p>
 * 校验放在 goods 侧而不是 admin 侧：05 §4.2 要求 admin 只做「鉴权 + 转发 + 日志」，
 * 分类层级、同级唯一、占用检查等业务判定全部由 goods 落库前后给出，
 * 这样 /inner 直连与经 admin 转发两条路径的行为完全一致。
 * </p>
 * <p>
 * 全部失败都抛 {@link GoodsApiException#paramInvalid(String)}（400 {@code PARAM_INVALID}）——
 * 05 §1.2 D 裁定：分类层级超限等<b>不</b>单列错误码，细分只进 msg。
 * </p>
 *
 * @since 2026-11-01
 */
public final class CatalogSupport {

    /** 分类名上限（08 v4 CN-22：1–32） */
    public static final int MAX_CATEGORY_NAME = 32;

    /** 品牌名上限（08 v4 CN-26：1–64） */
    public static final int MAX_BRAND_NAME = 64;

    /** 图片/图标/关键字/描述类字段上限（库列均为 varchar(255)） */
    public static final int MAX_URL = 255;

    /** 分类关键字上限 */
    public static final int MAX_KEYWORDS = 255;

    /** 描述上限 */
    public static final int MAX_DESC = 255;

    /** 品牌最低价上限（分）：DECIMAL(10,2) 元 ⇒ 99999999.99 元 ⇒ 9999999999 分 */
    public static final long MAX_FLOOR_PRICE_CENTS = 9_999_999_999L;

    /** 时间出参/入参统一格式（08 全局口径） */
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CatalogSupport() {
    }

    /* ==================== 文本 ==================== */

    /**
     * 必填文本：去首尾空白，空 ⇒ 400，超长 ⇒ 400（不截断）。
     *
     * @param raw    原始值
     * @param maxLen 最大长度
     * @param field  字段中文名（用于 msg）
     * @return 去空白后的值
     */
    public static String requiredText(String raw, int maxLen, String field) {
        String value = trimToNull(raw);
        if (value == null) {
            throw GoodsApiException.paramInvalid(field + "不能为空");
        }
        if (value.length() > maxLen) {
            throw GoodsApiException.paramInvalid(field + "长度不能超过 " + maxLen + " 位");
        }
        return value;
    }

    /**
     * 可选文本：去首尾空白，空 ⇒ {@code null}（调用方据此「未传即保持」），超长 ⇒ 400。
     *
     * @param raw    原始值
     * @param maxLen 最大长度
     * @param field  字段中文名
     * @return 去空白后的值或 null
     */
    public static String optionalText(String raw, int maxLen, String field) {
        String value = trimToNull(raw);
        if (value != null && value.length() > maxLen) {
            throw GoodsApiException.paramInvalid(field + "长度不能超过 " + maxLen + " 位");
        }
        return value;
    }

    /**
     * 去首尾空白；空白串归 {@code null}。
     *
     * @param raw 原始值
     * @return 去空白后的值或 null
     */
    public static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /* ==================== 排序 ==================== */

    /**
     * 排序值：&lt;0 ⇒ 400；未传 ⇒ 默认值。
     *
     * @param raw         原始排序值
     * @param defaultValue 缺省值
     * @return 归一后的排序值
     */
    public static int sortOrder(Integer raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw < 0) {
            throw GoodsApiException.paramInvalid("排序值不能小于 0");
        }
        return raw;
    }

    /* ==================== 金额（元 ↔ 分） ==================== */

    /**
     * 库内「元」转出参「整数分」（CN-25 {@code floorPrice}）。
     *
     * @param yuan 库内金额，可空
     * @return 整数分；null 视为 0
     */
    public static long toCents(BigDecimal yuan) {
        if (yuan == null) {
            return 0L;
        }
        return yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * 入参「整数分」转库内「元」（DECIMAL(10,2)）。
     *
     * @param cents 整数分，可空
     * @return 元（两位小数）；null 视为 0
     */
    public static BigDecimal toYuan(Long cents) {
        long value = cents == null ? 0L : cents;
        return BigDecimal.valueOf(value).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * 品牌最低价：&lt;0 或超出 {@code DECIMAL(10,2)} 范围 ⇒ 400；未传 ⇒ 默认 0。
     *
     * @param cents 原始分（可空）
     * @return 归一后的分
     */
    public static long floorPrice(Long cents) {
        if (cents == null) {
            return 0L;
        }
        if (cents < 0) {
            throw GoodsApiException.paramInvalid("品牌商品最低价不能小于 0");
        }
        if (cents > MAX_FLOOR_PRICE_CENTS) {
            throw GoodsApiException.paramInvalid("品牌商品最低价超出范围");
        }
        return cents;
    }

    /* ==================== 时间 ==================== */

    /**
     * 出参时间格式化。
     *
     * @param time 时间，可空
     * @return {@code yyyy-MM-dd HH:mm:ss} 或 null
     */
    public static String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }

    /**
     * 并发校验回传的 {@code updateTime}：空 ⇒ null（不做 CAS），格式错 ⇒ 400。
     *
     * @param raw 原始串
     * @return 解析后的时间或 null
     */
    public static LocalDateTime parseUpdateTime(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw GoodsApiException.paramInvalid("updateTime 格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    /**
     * 分类层级：由 {@code pid} 推导（{@code pid=0} ⇒ "1"，否则 "2"）。
     * <p>
     * 不直接读库里的 {@code level} 列：历史数据是 {@code L1/L2}，而 R2 口径（05 §1.5.1）
     * 是 {@code "1"/"2"}；由 {@code pid} 推导可同时兼容两种历史值。
     * </p>
     *
     * @param pid 父分类ID
     * @return "1" 或 "2"
     */
    public static String levelOf(Long pid) {
        return pid == null || pid == 0L ? "1" : "2";
    }
}
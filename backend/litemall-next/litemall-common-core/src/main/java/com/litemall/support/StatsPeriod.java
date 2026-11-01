package com.litemall.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 报表统计期（CI-08 / CI-100 共用口径，R2-00 §1.6）。
 * </p>
 * <p>
 * 口径（冻结）：
 * </p>
 * <ul>
 *   <li>时区固定 {@code Asia/Shanghai}，统计期按<b>自然日</b>：{@code [期初 00:00:00, 次日 00:00:00)}；</li>
 *   <li>{@code today} = 今日；{@code 7d} = 含今日往前 7 个自然日；{@code 30d} = 含今日往前 30 个自然日；</li>
 *   <li>非枚举值（含 null、大小写不符、任意串）<b>不报错</b>，静默落默认 {@code 30d}（06 §3「非法值忽略落默认」）；</li>
 *   <li>出参 {@code periodStart} 为 {@code yyyy-MM-dd 00:00:00}，{@code periodEnd} 为期末当日
 *       {@code 23:59:59}（展示用）；SQL 一律用 {@link #start()} / {@link #endExclusive()} 做
 *       {@code >= start AND < end}，<b>禁止 BETWEEN</b>（避免 23:59:59.999 漏数）。</li>
 * </ul>
 * <p>
 * admin 与 order 两个服务共用本类，保证「同一份口径、两处实现逐字相同」。
 * </p>
 *
 * @since 2026-11-01
 */
public final class StatsPeriod {

    /** 统计期：今日 */
    public static final String TODAY = "today";
    /** 统计期：含今日往前 7 个自然日 */
    public static final String SEVEN_DAYS = "7d";
    /** 统计期：含今日往前 30 个自然日 */
    public static final String THIRTY_DAYS = "30d";
    /** 默认统计期（契约：缺省/非法一律 30d） */
    public static final String DEFAULT = THIRTY_DAYS;

    /** 统计时区（R2-00 §1.6 冻结） */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_END = DateTimeFormatter.ofPattern("yyyy-MM-dd 23:59:59");

    private final String code;
    private final LocalDate startDate;
    private final LocalDate endDate;

    private StatsPeriod(String code, LocalDate startDate, LocalDate endDate) {
        this.code = code;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 解析统计期（以 {@code Asia/Shanghai} 的今日为期末）。
     *
     * @param raw 原始 {@code period} 入参，可空
     * @return 生效统计期；非法值静默落 {@code 30d}
     */
    public static StatsPeriod resolve(String raw) {
        return resolve(raw, LocalDate.now(ZONE));
    }

    /**
     * 解析统计期（指定期末日期，便于单测）。
     *
     * @param raw   原始 {@code period} 入参，可空
     * @param today 期末日期（含）
     * @return 生效统计期；非法值静默落 {@code 30d}
     */
    public static StatsPeriod resolve(String raw, LocalDate today) {
        String code = normalize(raw);
        int days = switch (code) {
            case TODAY -> 1;
            case SEVEN_DAYS -> 7;
            default -> 30;
        };
        return new StatsPeriod(code, today.minusDays(days - 1L), today);
    }

    /**
     * 入参归一：命中枚举返回规范写法，否则返回默认 {@code 30d}（不抛异常）。
     *
     * @param raw 原始入参
     * @return {@code today}/{@code 7d}/{@code 30d}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return DEFAULT;
        }
        String value = raw.trim();
        if (TODAY.equalsIgnoreCase(value)) {
            return TODAY;
        }
        if (SEVEN_DAYS.equalsIgnoreCase(value)) {
            return SEVEN_DAYS;
        }
        return DEFAULT;
    }

    /** @return 生效统计期标识：{@code today}/{@code 7d}/{@code 30d} */
    public String code() {
        return code;
    }

    /** @return 期初日期（含） */
    public LocalDate startDate() {
        return startDate;
    }

    /** @return 期末日期（含，即今日） */
    public LocalDate endDate() {
        return endDate;
    }

    /** @return 期初 {@code 00:00:00}（SQL 用 {@code >=}） */
    public LocalDateTime start() {
        return startDate.atStartOfDay();
    }

    /** @return 次日 {@code 00:00:00}（SQL 用 {@code <}，不用 BETWEEN） */
    public LocalDateTime endExclusive() {
        return endDate.plusDays(1).atStartOfDay();
    }

    /** @return 统计期自然日天数（trend 长度：1/7/30） */
    public int days() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** @return 期初展示串 {@code yyyy-MM-dd 00:00:00} */
    public String startText() {
        return start().format(DATE_TIME);
    }

    /** @return 期末展示串 {@code yyyy-MM-dd 23:59:59} */
    public String endText() {
        return endDate.atStartOfDay().format(DISPLAY_END);
    }

    /**
     * 完整自然日序列（含首尾，长度 = {@link #days()}），用于 trend 补 0。
     *
     * @return {@code yyyy-MM-dd} 升序列表
     */
    public List<String> dates() {
        List<String> dates = new ArrayList<>(days());
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            dates.add(day.format(DATE));
        }
        return dates;
    }
}
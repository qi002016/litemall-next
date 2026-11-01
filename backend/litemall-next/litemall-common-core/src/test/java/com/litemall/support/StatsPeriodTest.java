package com.litemall.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2（CI-08 / CI-100）：统计期口径单测（05-R2 实施口径 §1.6）。
 */
class StatsPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Test
    @DisplayName("today：期初=期末=今日，窗口 [今日00:00, 次日00:00)")
    void todayWindow() {
        StatsPeriod period = StatsPeriod.resolve("today", TODAY);

        assertThat(period.code()).isEqualTo("today");
        assertThat(period.days()).isEqualTo(1);
        assertThat(period.startDate()).isEqualTo(TODAY);
        assertThat(period.endDate()).isEqualTo(TODAY);
        assertThat(period.start()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(period.endExclusive()).isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
        assertThat(period.startText()).isEqualTo("2026-11-01 00:00:00");
        assertThat(period.endText()).isEqualTo("2026-11-01 23:59:59");
    }

    @Test
    @DisplayName("7d：含今日往前 7 个自然日（不是 6 天，也不是 8 天）")
    void sevenDaysWindow() {
        StatsPeriod period = StatsPeriod.resolve("7d", TODAY);

        assertThat(period.code()).isEqualTo("7d");
        assertThat(period.days()).isEqualTo(7);
        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(period.endDate()).isEqualTo(TODAY);
        assertThat(period.dates()).hasSize(7)
                .startsWith("2026-10-26")
                .endsWith("2026-11-01");
    }

    @Test
    @DisplayName("30d：含今日往前 30 个自然日")
    void thirtyDaysWindow() {
        StatsPeriod period = StatsPeriod.resolve("30d", TODAY);

        assertThat(period.code()).isEqualTo("30d");
        assertThat(period.days()).isEqualTo(30);
        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(period.dates()).hasSize(30);
    }

    @Test
    @DisplayName("缺省与非法值静默落 30d（不报 400，§2.1.1）")
    void invalidFallsBackToDefault() {
        assertThat(StatsPeriod.resolve(null).code()).isEqualTo("30d");
        assertThat(StatsPeriod.resolve("").code()).isEqualTo("30d");
        assertThat(StatsPeriod.resolve("90d").code()).isEqualTo("30d");
        assertThat(StatsPeriod.resolve("  ").code()).isEqualTo("30d");
        assertThat(StatsPeriod.resolve("month").code()).isEqualTo("30d");
        assertThat(StatsPeriod.resolve(null, TODAY).startDate()).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    @DisplayName("大小写与首尾空白容错：TODAY / 7D / ' 30d '")
    void normalizeToleratesCaseAndBlank() {
        assertThat(StatsPeriod.normalize("TODAY")).isEqualTo("today");
        assertThat(StatsPeriod.normalize(" 7D ")).isEqualTo("7d");
        assertThat(StatsPeriod.normalize(" 30d ")).isEqualTo("30d");
    }

    @Test
    @DisplayName("趋势序列：升序、连续、长度=days")
    void datesAreAscendingAndContiguous() {
        List<String> dates = StatsPeriod.resolve("30d", TODAY).dates();

        assertThat(dates).isSorted();
        assertThat(dates.get(dates.size() - 1)).isEqualTo("2026-11-01");
        assertThat(dates.get(0)).isEqualTo("2026-10-03");
        assertThat(dates).doesNotHaveDuplicates();
    }
}
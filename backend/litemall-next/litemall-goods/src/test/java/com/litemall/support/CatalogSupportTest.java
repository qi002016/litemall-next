package com.litemall.support;

import com.litemall.exception.GoodsApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CatalogSupport} 纯函数测试（R2-01 分类/品牌入参校验与金额换算）。
 *
 * <p>
 * 这些规则被 admin 与 goods 两侧共用，出错会直接改坏库里的金额或层级，
 * 因此单独钉住：元↔分换算不带浮点误差、{@code pid} 推导 level 兼容历史 {@code L1/L2}、
 * 超长/空值一律 400 {@code PARAM_INVALID}（05 §1.2 D：不单列错误码）。
 * </p>
 */
class CatalogSupportTest {

    @Test
    @DisplayName("金额：元→分与分→元互为逆运算，不接受浮点误差")
    void moneyConversion() {
        assertThat(CatalogSupport.toCents(new BigDecimal("19.99"))).isEqualTo(1999L);
        assertThat(CatalogSupport.toCents(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(CatalogSupport.toCents(new BigDecimal("100000.00"))).isEqualTo(10_000_000L);
        assertThat(CatalogSupport.toCents(null)).isZero();

        assertThat(CatalogSupport.toYuan(1999L)).isEqualByComparingTo("19.99");
        assertThat(CatalogSupport.toYuan(0L)).isEqualByComparingTo("0.00");
        assertThat(CatalogSupport.toYuan(null)).isEqualByComparingTo("0.00");
        // 库列 DECIMAL(10,2)：出参分必须是两位小数元，不能是 19.9
        assertThat(CatalogSupport.toYuan(1990L).toPlainString()).isEqualTo("19.90");
    }

    @Test
    @DisplayName("金额：floorPrice 负数或超上限 ⇒ 400，未传 ⇒ 0")
    void floorPriceRange() {
        assertThat(CatalogSupport.floorPrice(null)).isZero();
        assertThat(CatalogSupport.floorPrice(0L)).isZero();
        assertThat(CatalogSupport.floorPrice(CatalogSupport.MAX_FLOOR_PRICE_CENTS))
                .isEqualTo(CatalogSupport.MAX_FLOOR_PRICE_CENTS);

        GoodsApiException negative = assertThrows(GoodsApiException.class,
                () -> CatalogSupport.floorPrice(-1L));
        assertThat(negative.getErrorCode()).isEqualTo("PARAM_INVALID");

        GoodsApiException tooLarge = assertThrows(GoodsApiException.class,
                () -> CatalogSupport.floorPrice(CatalogSupport.MAX_FLOOR_PRICE_CENTS + 1));
        assertThat(tooLarge.getErrorCode()).isEqualTo("PARAM_INVALID");
    }

    @Test
    @DisplayName("level：由 pid 推导，兼容库里历史 L1/L2 值")
    void levelFromPid() {
        assertThat(CatalogSupport.levelOf(null)).isEqualTo("1");
        assertThat(CatalogSupport.levelOf(0L)).isEqualTo("1");
        assertThat(CatalogSupport.levelOf(123L)).isEqualTo("2");
    }

    @Test
    @DisplayName("文本：必填空/超长 ⇒ 400；可选空 ⇒ null；首尾空白被去掉")
    void textValidation() {
        assertThat(CatalogSupport.requiredText("  服饰  ", 32, "分类名称")).isEqualTo("服饰");
        assertThat(CatalogSupport.optionalText("   ", 255, "分类图片")).isNull();
        assertThat(CatalogSupport.optionalText(null, 255, "分类图片")).isNull();

        GoodsApiException blank = assertThrows(GoodsApiException.class,
                () -> CatalogSupport.requiredText("   ", 32, "分类名称"));
        assertThat(blank.getMessage()).isEqualTo("分类名称不能为空");

        GoodsApiException tooLong = assertThrows(GoodsApiException.class,
                () -> CatalogSupport.requiredText("x".repeat(33), 32, "分类名称"));
        assertThat(tooLong.getMessage()).isEqualTo("分类名称长度不能超过 32 位");

        // 超长必须 400 而不是截断（05 §1.5）
        GoodsApiException optionalTooLong = assertThrows(GoodsApiException.class,
                () -> CatalogSupport.optionalText("x".repeat(256), 255, "分类图片"));
        assertThat(optionalTooLong.getErrorCode()).isEqualTo("PARAM_INVALID");
    }

    @Test
    @DisplayName("排序值：负数 ⇒ 400；未传 ⇒ 默认")
    void sortOrderValidation() {
        assertThat(CatalogSupport.sortOrder(null, 0)).isZero();
        assertThat(CatalogSupport.sortOrder(9, 0)).isEqualTo(9);
        GoodsApiException negative = assertThrows(GoodsApiException.class,
                () -> CatalogSupport.sortOrder(-1, 0));
        assertThat(negative.getMessage()).isEqualTo("排序值不能小于 0");
    }

    @Test
    @DisplayName("时间：yyyy-MM-dd HH:mm:ss 往返；格式错 ⇒ 400；空 ⇒ null（不做 CAS）")
    void updateTimeParsing() {
        assertThat(CatalogSupport.parseUpdateTime("2026-11-01 19:05:43").toString())
                .isEqualTo("2026-11-01T19:05:43");
        assertThat(CatalogSupport.parseUpdateTime("  ")).isNull();

        GoodsApiException bad = assertThrows(GoodsApiException.class,
                () -> CatalogSupport.parseUpdateTime("2026/11/01 19:05"));
        assertThat(bad.getMessage()).isEqualTo("updateTime 格式应为 yyyy-MM-dd HH:mm:ss");

        assertThat(CatalogSupport.formatTime(null)).isNull();
        assertThat(CatalogSupport.formatTime(java.time.LocalDateTime.of(2026, 11, 1, 9, 8, 7)))
                .isEqualTo("2026-11-01 09:08:07");
    }
}
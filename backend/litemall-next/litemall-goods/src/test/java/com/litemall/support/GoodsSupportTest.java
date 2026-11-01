package com.litemall.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GoodsSupport} 纯函数单测。
 *
 * <p>
 * 覆盖 R1 商品域三件最容易出错的口径：①元 ↔ 整数分；②spec_value_ids / sku_code 归一
 * （升序、去重、无规格占位 D）；③三量 number / locked / sellable（locked 未知时必须为
 * {@code null} 而不是 0，否则会虚报可售导致超卖）。
 * </p>
 */
class GoodsSupportTest {

    @Test
    @DisplayName("金额换算：元 → 整数分，四舍五入在分位；null 透传")
    void yuanToFen() {
        assertThat(GoodsSupport.yuanToFen(new BigDecimal("12.34"))).isEqualTo(1234);
        assertThat(GoodsSupport.yuanToFen(new BigDecimal("0.01"))).isEqualTo(1);
        assertThat(GoodsSupport.yuanToFen(new BigDecimal("10.005"))).isEqualTo(1001);
        assertThat(GoodsSupport.yuanToFen(null)).isNull();
    }

    @Test
    @DisplayName("金额换算：整数分 → 元，两位小数；null 透传")
    void fenToYuan() {
        assertThat(GoodsSupport.fenToYuan(1234)).isEqualTo(new BigDecimal("12.34"));
        assertThat(GoodsSupport.fenToYuan(1)).isEqualTo(new BigDecimal("0.01"));
        assertThat(GoodsSupport.fenToYuan(null)).isNull();
    }

    @Test
    @DisplayName("spec_value_ids 解析：去空白、去重、数值升序")
    void parseSpecValueIds() {
        assertThat(GoodsSupport.parseSpecValueIds(" 3, 1 ,3")).containsExactly("1", "3");
        assertThat(GoodsSupport.parseSpecValueIds("10,9")).containsExactly("9", "10");
        assertThat(GoodsSupport.parseSpecValueIds("")).isEmpty();
        assertThat(GoodsSupport.parseSpecValueIds(null)).isEmpty();
    }

    @Test
    @DisplayName("spec_value_ids 归一：升序去重逗号串，空集合为空串")
    void joinSpecValueIds() {
        assertThat(GoodsSupport.joinSpecValueIds(List.of("3", "1", "1"))).isEqualTo("1,3");
        assertThat(GoodsSupport.joinSpecValueIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("sku_code：有规格 goodsId-<升序ID用_连接>；无规格 goodsId-D")
    void buildSkuCode() {
        assertThat(GoodsSupport.buildSkuCode(7L, List.of("3", "1"))).isEqualTo("7-1_3");
        assertThat(GoodsSupport.buildSkuCode(7L, List.of())).isEqualTo("7-D");
    }

    @Test
    @DisplayName("规格文本：按维度顺序以 ' / ' 连接，空值丢弃")
    void buildSpecText() {
        assertThat(GoodsSupport.buildSpecText(List.of("白", "350ml"))).isEqualTo("白 / 350ml");
        assertThat(GoodsSupport.buildSpecText(List.of("白", " "))).isEqualTo("白");
        assertThat(GoodsSupport.buildSpecText(List.of())).isEmpty();
    }

    @Test
    @DisplayName("可售量：max(0, number − locked)；locked 不可用时为 null（不得当 0）")
    void sellable() {
        assertThat(GoodsSupport.sellable(10, 3, false)).isEqualTo(7);
        assertThat(GoodsSupport.sellable(2, 5, false)).isZero();
        assertThat(GoodsSupport.sellable(10, null, false)).isEqualTo(10);
        assertThat(GoodsSupport.sellable(10, 3, true)).isNull();
    }

    @Test
    @DisplayName("低库存：sellable ≤ 5；未知不算低库存")
    void lowStock() {
        assertThat(GoodsSupport.lowStock(5)).isTrue();
        assertThat(GoodsSupport.lowStock(0)).isTrue();
        assertThat(GoodsSupport.lowStock(6)).isFalse();
        assertThat(GoodsSupport.lowStock(null)).isFalse();
        assertThat(GoodsSupport.LOW_STOCK_THRESHOLD).isEqualTo(5);
    }

    @Test
    @DisplayName("汇总可售量：任一项未知则整体未知")
    void sumSellable() {
        assertThat(GoodsSupport.sumSellable(List.of(1, 2), false)).isEqualTo(3);
        assertThat(GoodsSupport.sumSellable(List.of(1, 2), true)).isNull();
    }

    @Test
    @DisplayName("时间：统一 yyyy-MM-dd HH:mm:ss 出参，解析可回环")
    void time() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 1, 8, 5, 9);
        assertThat(GoodsSupport.formatTime(time)).isEqualTo("2026-11-01 08:05:09");
        assertThat(GoodsSupport.parseTime("2026-11-01 08:05:09")).isEqualTo(time);
        assertThat(GoodsSupport.formatTime(null)).isNull();
        assertThat(GoodsSupport.parseTime(" ")).isNull();
    }

    @Test
    @DisplayName("状态文案：0 草稿 / 1 在售 / 2 已下架")
    void statusText() {
        assertThat(GoodsSupport.statusText(0)).isEqualTo("草稿");
        assertThat(GoodsSupport.statusText(1)).isEqualTo("在售");
        assertThat(GoodsSupport.statusText(2)).isEqualTo("已下架");
    }
}
package com.litemall.domain.DTO;

import com.litemall.exception.GoodsApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 查询参数归一单测（CI-31 / CI-47 / CN-13）。
 *
 * <p>
 * 归一规则决定契约边界：分页上限、关键字长度、价格区间颠倒、排序白名单非法值「忽略不报错」、
 * 状态多值只允许 0/1/2。
 * </p>
 */
class QueryNormalizeTest {

    @Test
    @DisplayName("CI-31：缺省分页 1/20，超上限截断为 100，空白关键字忽略")
    void goodsListDefaults() {
        GoodsListQuery query = new GoodsListQuery();
        query.normalize();
        assertThat(query.getPageNum()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(20);
        assertThat(query.getKeyword()).isNull();
        assertThat(query.getOnlyStock()).isFalse();

        GoodsListQuery big = new GoodsListQuery();
        big.setPageNum(0);
        big.setPageSize(1000);
        big.setKeyword("   ");
        big.normalize();
        assertThat(big.getPageNum()).isEqualTo(1);
        assertThat(big.getPageSize()).isEqualTo(100);
        assertThat(big.getKeyword()).isNull();
    }

    @Test
    @DisplayName("CI-31：minP > maxP 自动交换；负数报 PARAM_INVALID")
    void goodsListPriceRange() {
        GoodsListQuery query = new GoodsListQuery();
        query.setMinP(900);
        query.setMaxP(100);
        query.normalize();
        assertThat(query.getMinP()).isEqualTo(100);
        assertThat(query.getMaxP()).isEqualTo(900);

        GoodsListQuery negative = new GoodsListQuery();
        negative.setMinP(-1);
        assertThatThrownBy(negative::normalize)
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("minP");
    }

    @Test
    @DisplayName("CI-31：排序白名单 price_asc/price_desc/new，非法值忽略（返回 null 落默认）")
    void goodsListSort() {
        assertThat(GoodsListQuery.normalizeSort("price_asc")).isEqualTo("price_asc");
        assertThat(GoodsListQuery.normalizeSort(" NEW ")).isEqualTo("new");
        assertThat(GoodsListQuery.normalizeSort("sales_desc")).isNull();
        assertThat(GoodsListQuery.normalizeSort(null)).isNull();
    }

    @Test
    @DisplayName("CI-31：关键字超过 50 字报 PARAM_INVALID")
    void goodsListKeywordTooLong() {
        GoodsListQuery query = new GoodsListQuery();
        query.setKeyword("x".repeat(51));
        assertThatThrownBy(query::normalize)
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("50");
    }

    @Test
    @DisplayName("CI-47：status 逗号多值，只允许 0/1/2；非法报 PARAM_INVALID")
    void adminStatuses() {
        assertThat(GoodsAdminQuery.parseStatuses("1,2")).containsExactly(1, 2);
        assertThat(GoodsAdminQuery.parseStatuses(" 1 , 1 ")).containsExactly(1);
        assertThat(GoodsAdminQuery.parseStatuses(null)).isEmpty();
        assertThatThrownBy(() -> GoodsAdminQuery.parseStatuses("3"))
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("status");
        assertThatThrownBy(() -> GoodsAdminQuery.parseStatuses("abc"))
                .isInstanceOf(GoodsApiException.class);
    }

    @Test
    @DisplayName("CI-47：未传 status 且 onSale=true ⇒ 视为 status=1")
    void adminOnSaleFallback() {
        GoodsAdminQuery query = new GoodsAdminQuery();
        query.setOnSale(true);
        query.normalize();
        assertThat(query.getStatuses()).containsExactly(1);
    }

    @Test
    @DisplayName("CN-13：排序白名单 sellable/number/updateTime，非法落 sellable")
    void stockSort() {
        StockAdminQuery query = new StockAdminQuery();
        assertThat(query.sortByOrDefault()).isEqualTo("sellable");

        query.setSortBy("number");
        assertThat(query.sortByOrDefault()).isEqualTo("number");

        query.setSortBy("locked");
        assertThat(query.sortByOrDefault()).isEqualTo("sellable");
    }

    @Test
    @DisplayName("CN-13：goodsStatus 多值校验与 goodsStatus 非法提示")
    void stockStatuses() {
        StockAdminQuery query = new StockAdminQuery();
        query.setGoodsStatus("0,1");
        assertThat(query.parseStatuses()).containsExactly(0, 1);

        StockAdminQuery bad = new StockAdminQuery();
        bad.setGoodsStatus("9");
        assertThatThrownBy(bad::parseStatuses)
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("goodsStatus");
    }

    @Test
    @DisplayName("CN-13：分页缺省 1/10，超上限截断为 100")
    void stockPaging() {
        StockAdminQuery query = new StockAdminQuery();
        assertThat(query.pageNumOrDefault()).isEqualTo(1);
        assertThat(query.pageSizeOrDefault()).isEqualTo(10);

        query.setPageSize(500);
        assertThat(query.pageSizeOrDefault()).isEqualTo(100);
    }
}
package com.litemall.domain.DTO;

import com.litemall.exception.GoodsApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2-04 {@link CommentAdminQuery} 归一与校验的纯单元测试（05 §1.4、§3.9）。
 *
 * <p>
 * 这些规则与 {@code GoodsAdminQuery} / {@code BrandAdminQuery} 保持同一套：
 * 分页缺省 1/20 上限 100、关键字 ≤50、逗号多值白名单、时间格式固定，
 * 非法一律 400 {@code PARAM_INVALID}（对外仍是 HTTP 200）。
 * </p>
 */
class CommentAdminQueryTest {

    @Test
    @DisplayName("分页：缺省 1/20，超上限夹到 100，非法值回落缺省")
    void normalizePaging() {
        CommentAdminQuery query = new CommentAdminQuery();
        query.normalize();
        assertThat(query.getPageNum()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(20);

        CommentAdminQuery big = new CommentAdminQuery();
        big.setPageNum(0);
        big.setPageSize(1000);
        big.normalize();
        assertThat(big.getPageNum()).isEqualTo(1);
        assertThat(big.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("keyword：去首尾空白、空串不筛、超 50 字 400")
    void normalizeKeyword() {
        CommentAdminQuery blank = new CommentAdminQuery();
        blank.setKeyword("   ");
        blank.normalize();
        assertThat(blank.getKeyword()).isNull();

        CommentAdminQuery trimmed = new CommentAdminQuery();
        trimmed.setKeyword("  物流  ");
        trimmed.normalize();
        assertThat(trimmed.getKeyword()).isEqualTo("物流");

        CommentAdminQuery tooLong = new CommentAdminQuery();
        tooLong.setKeyword("字".repeat(51));
        assertThatThrownBy(tooLong::normalize)
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("搜索关键字最多 50 字");
    }

    @Test
    @DisplayName("star / status：逗号多值去重保序，越界或非数值 400")
    void normalizeMultiValues() {
        CommentAdminQuery query = new CommentAdminQuery();
        query.setStar("1, 5,5");
        query.setStatus("0,1");
        query.normalize();
        assertThat(query.getStars()).containsExactly(1, 5);
        assertThat(query.getStatuses()).containsExactly(0, 1);

        CommentAdminQuery starOutOfRange = new CommentAdminQuery();
        starOutOfRange.setStar("6");
        assertThatThrownBy(starOutOfRange::normalize)
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("star 取值非法");

        CommentAdminQuery statusInvalid = new CommentAdminQuery();
        statusInvalid.setStatus("2");
        assertThatThrownBy(statusInvalid::normalize)
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("status 取值非法");

        CommentAdminQuery notNumber = new CommentAdminQuery();
        notNumber.setStar("abc");
        assertThatThrownBy(notNumber::normalize)
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("star 取值非法");
    }

    @Test
    @DisplayName("时间：格式固定 yyyy-MM-dd HH:mm:ss，起 > 止 400")
    void normalizeTime() {
        CommentAdminQuery query = new CommentAdminQuery();
        query.setDateFrom("2026-01-01 00:00:00");
        query.setDateTo("2026-01-31 23:59:59");
        query.normalize();
        assertThat(query.getFromTime()).isNotNull();
        assertThat(query.getToTime()).isAfter(query.getFromTime());

        CommentAdminQuery badFormat = new CommentAdminQuery();
        badFormat.setDateFrom("2026-01-01");
        assertThatThrownBy(badFormat::normalize)
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("dateFrom 格式应为 yyyy-MM-dd HH:mm:ss");

        CommentAdminQuery reversed = new CommentAdminQuery();
        reversed.setDateFrom("2026-02-01 00:00:00");
        reversed.setDateTo("2026-01-01 00:00:00");
        assertThatThrownBy(reversed::normalize)
                .isInstanceOf(GoodsApiException.class)
                .hasMessageContaining("dateFrom 不能晚于 dateTo");
    }
}
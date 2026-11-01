package com.litemall.support;

import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R1（R1-05）：admin 侧纯函数支持类单测。
 * <p>
 * 这些函数是「转发前唯一允许存在的校验」：分页/排序归一、状态标识白名单、
 * 商品 status ↔ onSale 映射、最低售价规则。业务判定（订单/售后状态机、库存计算）
 * 一律在对端，不在此处。
 * </p>
 */
class AdminSupportTest {

    /* ==================== AdminQuerySupport ==================== */

    @Test
    @DisplayName("分页：缺省/0/负数按默认，超 100 截断（06 §3.1）")
    void pageNormalization() {
        assertThat(AdminQuerySupport.pageNum(null)).isEqualTo(1);
        assertThat(AdminQuerySupport.pageNum(0)).isEqualTo(1);
        assertThat(AdminQuerySupport.pageNum(-3)).isEqualTo(1);
        assertThat(AdminQuerySupport.pageNum(4)).isEqualTo(4);

        assertThat(AdminQuerySupport.pageSize(null)).isEqualTo(20);
        assertThat(AdminQuerySupport.pageSize(0)).isEqualTo(20);
        assertThat(AdminQuerySupport.pageSize(30)).isEqualTo(30);
        assertThat(AdminQuerySupport.pageSize(500)).isEqualTo(100);
    }

    @Test
    @DisplayName("排序：白名单外/空落默认值、大小写不敏感，绝不报错（06 §3.1）")
    void sortByFallsBack() {
        Set<String> allowed = Set.of("addTime", "orderAmount");
        assertThat(AdminQuerySupport.sortBy(null, allowed, "addTime")).isEqualTo("addTime");
        assertThat(AdminQuerySupport.sortBy("  ", allowed, "addTime")).isEqualTo("addTime");
        assertThat(AdminQuerySupport.sortBy("bogus", allowed, "addTime")).isEqualTo("addTime");
        assertThat(AdminQuerySupport.sortBy("ORDERTIME", allowed, "addTime")).isEqualTo("addTime");
        assertThat(AdminQuerySupport.sortBy(" orderamount ", allowed, "addTime")).isEqualTo("orderAmount");
    }

    @Test
    @DisplayName("状态多值：逗号拆分+去重+空白剔除；非法 token → 400 PARAM_INVALID")
    void csvTokensValidation() {
        Set<String> allowed = Set.of("unpaid", "undelivered", "canceled");
        assertThat(AdminQuerySupport.csvTokens(null, allowed, "status")).isNull();
        assertThat(AdminQuerySupport.csvTokens(" , ", allowed, "status")).isNull();
        assertThat(AdminQuerySupport.csvTokens("unpaid,undelivered,unpaid", allowed, "status"))
                .isEqualTo("unpaid,undelivered");

        assertThatThrownBy(() -> AdminQuerySupport.csvTokens("unpaid,closed", allowed, "status"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("status 取值非法：closed");
    }

    @Test
    @DisplayName("文本：trim 后空转 null，超长 400 PARAM_INVALID，默认上限 64")
    void textNormalization() {
        assertThat(AdminQuerySupport.text("  ", "keyword")).isNull();
        assertThat(AdminQuerySupport.text(" 张三 ", "keyword")).isEqualTo("张三");
        assertThat(AdminQuerySupport.text("x".repeat(64), "keyword")).hasSize(64);
        assertThatThrownBy(() -> AdminQuerySupport.text("x".repeat(65), "keyword"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("keyword");
        assertThatThrownBy(() -> AdminQuerySupport.text("x".repeat(51), 50, "keyword"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("50");
    }

    @Test
    @DisplayName("时间：仅接受 yyyy-MM-dd HH:mm:ss，其余 400")
    void dateTimeFormat() {
        assertThat(AdminQuerySupport.dateTime(null, "addTimeMin")).isNull();
        assertThat(AdminQuerySupport.dateTime("2026-01-02 03:04:05", "addTimeMin"))
                .isEqualTo("2026-01-02 03:04:05");
        assertThatThrownBy(() -> AdminQuerySupport.dateTime("2026-01-02", "addTimeMin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("addTimeMin");
        assertThatThrownBy(() -> AdminQuerySupport.dateTime("2026-01-02T03:04:05", "addTimeMin"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("布尔：空按默认值")
    void boolDefault() {
        assertThat(AdminQuerySupport.bool(null, true)).isTrue();
        assertThat(AdminQuerySupport.bool(false, true)).isFalse();
    }

    /* ==================== GoodsStatusSupport ==================== */

    @Test
    @DisplayName("商品状态：新建缺省为草稿（0）；status 与 onSale 一致时取 status")
    void goodsStatusCreate() {
        assertThat(GoodsStatusSupport.resolveForCreate(null, null)).isEqualTo(GoodsStatusSupport.DRAFT);
        assertThat(GoodsStatusSupport.resolveForCreate(null, true)).isEqualTo(GoodsStatusSupport.ON_SALE);
        assertThat(GoodsStatusSupport.resolveForCreate(null, false)).isEqualTo(GoodsStatusSupport.DRAFT);
        assertThat(GoodsStatusSupport.resolveForCreate(GoodsStatusSupport.ON_SALE, true))
                .isEqualTo(GoodsStatusSupport.ON_SALE);
        assertThat(GoodsStatusSupport.resolveForCreate(GoodsStatusSupport.OFF_SHELF, null))
                .isEqualTo(GoodsStatusSupport.OFF_SHELF);
    }

    @Test
    @DisplayName("商品状态：status 与 onSale 矛盾 → 400 STATUS_ON_SALE_CONFLICT")
    void goodsStatusConflict() {
        assertThatThrownBy(() -> GoodsStatusSupport.resolveForCreate(GoodsStatusSupport.OFF_SHELF, true))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GoodsStatusSupport.CONFLICT_CODE));
        assertThatThrownBy(() -> GoodsStatusSupport.resolveForUpdate(0, true))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("STATUS_ON_SALE_CONFLICT"));
    }

    @Test
    @DisplayName("商品状态：编辑时两者都缺省则不改编（返回 null）；越界 400")
    void goodsStatusUpdate() {
        assertThat(GoodsStatusSupport.resolveForUpdate(null, null)).isNull();
        assertThat(GoodsStatusSupport.resolveForUpdate(null, false)).isEqualTo(GoodsStatusSupport.DRAFT);
        assertThat(GoodsStatusSupport.resolveForUpdate(GoodsStatusSupport.OFF_SHELF, null))
                .isEqualTo(GoodsStatusSupport.OFF_SHELF);
        assertThatThrownBy(() -> GoodsStatusSupport.resolveForUpdate(9, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("商品状态只能是");
    }

    /* ==================== GoodsWriteSupport ==================== */

    @Test
    @DisplayName("最低售价：传了 SKU 时可空（由 SKU 推导）；无 SKU 时必填且 ≥1 分")
    void minPriceRules() {
        GoodsAdminDTO.SkuDTO sku = new GoodsAdminDTO.SkuDTO();
        GoodsWriteSupport.validatePrice(null, List.of(sku));
        GoodsWriteSupport.validatePrice(1, null);

        assertThatThrownBy(() -> GoodsWriteSupport.validatePrice(null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("minPrice");
        assertThatThrownBy(() -> GoodsWriteSupport.validatePrice(0, List.of()))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("PRICE_ILLEGAL"));
    }

    @Test
    @DisplayName("修改商品：minPrice 缺省表示沿用原价（允许只发 id+status），传了才校验下限")
    void minPriceOnUpdate() {
        // 上下架只发 {id,status}（GoodsList.vue:166）——不得被 400 拒绝
        GoodsWriteSupport.validatePriceOnUpdate(null);
        GoodsWriteSupport.validatePriceOnUpdate(1);

        assertThatThrownBy(() -> GoodsWriteSupport.validatePriceOnUpdate(0))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("PRICE_ILLEGAL"));
        assertThatThrownBy(() -> GoodsWriteSupport.validatePriceOnUpdate(-1))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("PRICE_ILLEGAL"));
    }
}
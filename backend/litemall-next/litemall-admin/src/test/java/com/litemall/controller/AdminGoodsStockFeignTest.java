package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.domain.DTO.GoodsCreateDTO;
import com.litemall.domain.DTO.GoodsUpdateDTO;
import com.litemall.domain.DTO.StockUpdateReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.domain.dto.StockAdminDTO;
import com.litemall.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R1（R1-05）：管理端商品（CI-09~CI-12）与库存（CN-13/CN-14）控制器的转发行为单测。
 * <p>
 * 覆盖点：
 * </p>
 * <ul>
 *   <li>CI-09：{@code keyword} 取代旧 {@code name}、分页/排序归一、{@code status} 与
 *       {@code onSale} 同传时以 {@code status} 为准；</li>
 *   <li>CI-10：{@code status↔onSale} 映射与矛盾 400、无 SKU 时 minPrice 必填、返回 {@code {id}}；</li>
 *   <li>CI-11：<b>部分更新</b>（只发 {@code {id,status}}，前端上下架的真实载荷）不得 NPE；
 *       {@code updateTime} 透传做 CAS；</li>
 *   <li>CI-12：删除透传 id；</li>
 *   <li>CN-13：排序默认 {@code sellable}、{@code goodsStatus} 白名单；</li>
 *   <li>CN-14：{@code number} 为绝对值原样透传、返回契约 6 字段。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminGoodsStockFeignTest {

    @Mock
    private GoodsClient goodsClient;

    private GoodsController goodsController;

    private StockController stockController;

    @BeforeEach
    void setUp() {
        goodsController = new GoodsController(goodsClient);
        stockController = new StockController(goodsClient);
    }

    /* ==================== CI-09 商品列表 ==================== */

    @Test
    @DisplayName("CI-09 keyword 取代旧 name：分页归一、keyword 去空白、status 优先于 onSale、非法排序落默认")
    void goodsListNormalizes() {
        when(goodsClient.adminListGoods(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageVO<>(0L, 0L, List.of()));

        Result<PageVO<GoodsAdminDTO>> result = goodsController.list(
                null, 500, "  T恤  ", 12L, null, "0,1", true, "bogus");

        assertThat(result.getCode()).isEqualTo(200);
        verify(goodsClient).adminListGoods(1, 100, "T恤", 12L, null, "0,1", null, "sortOrder");
    }

    @Test
    @DisplayName("CI-09 只传 onSale 时透传 onSale；status 非法 → 400 PARAM_INVALID 且不调下游")
    void goodsListOnSaleAndInvalidStatus() {
        when(goodsClient.adminListGoods(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageVO<>(0L, 0L, List.of()));
        goodsController.list(2, 10, null, null, null, null, true, "addTime");
        verify(goodsClient).adminListGoods(2, 10, null, null, null, null, true, "addTime");

        assertThatThrownBy(() -> goodsController.list(1, 10, null, null, null, "9", null, null))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("PARAM_INVALID"));
        verify(goodsClient, never()).adminListGoods(any(), any(), any(), any(), any(), eq("9"), any(), any());
    }

    /* ==================== CI-10 新建 ==================== */

    @Test
    @DisplayName("CI-10 新建：status 与 onSale 双向映射、返回 {id}、排序权重等字段透传")
    void goodsCreateMapsStatusAndReturnsId() {
        when(goodsClient.adminCreateGoods(any())).thenReturn(42L);

        GoodsCreateDTO body = new GoodsCreateDTO();
        body.setName("  R1-05自测-商品  ");
        body.setPicUrl("https://img/x.png");
        body.setCategoryId(7L);
        body.setMinPrice(100);
        body.setStatus(2);

        Result<com.litemall.domain.VO.IdVO> result = goodsController.create(body);

        assertThat(result.getData().id()).isEqualTo(42L);
        ArgumentCaptor<GoodsAdminDTO> captor = ArgumentCaptor.forClass(GoodsAdminDTO.class);
        verify(goodsClient).adminCreateGoods(captor.capture());
        GoodsAdminDTO sent = captor.getValue();
        assertThat(sent.getName()).isEqualTo("R1-05自测-商品");
        assertThat(sent.getStatus()).isEqualTo(2);
        assertThat(sent.getOnSale()).isFalse();
    }

    @Test
    @DisplayName("CI-10 status 与 onSale 矛盾 → 400 STATUS_ON_SALE_CONFLICT；无 SKU 且无价 → 400")
    void goodsCreateRejectsConflictAndMissingPrice() {
        GoodsCreateDTO conflict = new GoodsCreateDTO();
        conflict.setName("x");
        conflict.setPicUrl("https://img/x.png");
        conflict.setCategoryId(7L);
        conflict.setMinPrice(100);
        conflict.setStatus(1);
        conflict.setOnSale(false);
        assertThatThrownBy(() -> goodsController.create(conflict))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("STATUS_ON_SALE_CONFLICT"));

        GoodsCreateDTO noPrice = new GoodsCreateDTO();
        noPrice.setName("x");
        noPrice.setPicUrl("https://img/x.png");
        noPrice.setCategoryId(7L);
        noPrice.setStatus(0);
        assertThatThrownBy(() -> goodsController.create(noPrice))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("PARAM_INVALID"));

        verify(goodsClient, never()).adminCreateGoods(any());
    }

    /* ==================== CI-11 编辑 ==================== */

    @Test
    @DisplayName("CI-11 部分更新：只发 {id,status}（前端上下架真实载荷）不 NPE，且不带无关字段")
    void goodsUpdatePartialPayload() {
        GoodsUpdateDTO body = new GoodsUpdateDTO();
        body.setId(9L);
        body.setStatus(1);

        assertThat(goodsController.update(body).getCode()).isEqualTo(200);

        ArgumentCaptor<GoodsAdminDTO> captor = ArgumentCaptor.forClass(GoodsAdminDTO.class);
        verify(goodsClient).adminUpdateGoods(captor.capture());
        GoodsAdminDTO sent = captor.getValue();
        assertThat(sent.getId()).isEqualTo(9L);
        assertThat(sent.getStatus()).isEqualTo(1);
        assertThat(sent.getOnSale()).isTrue();
        assertThat(sent.getName()).isNull();
        assertThat(sent.getPicUrl()).isNull();
        assertThat(sent.getCategoryId()).isNull();
        assertThat(sent.getMinPrice()).isNull();
        assertThat(sent.getUpdateTime()).isNull();
    }

    @Test
    @DisplayName("CI-11 updateTime 回传做 CAS（去空白后透传）；传了 minPrice<1 → 400 PRICE_ILLEGAL")
    void goodsUpdateCarriesUpdateTimeAndValidatesPrice() {
        GoodsUpdateDTO body = new GoodsUpdateDTO();
        body.setId(9L);
        body.setUpdateTime("  2026-11-01 10:00:00  ");
        goodsController.update(body);

        ArgumentCaptor<GoodsAdminDTO> captor = ArgumentCaptor.forClass(GoodsAdminDTO.class);
        verify(goodsClient).adminUpdateGoods(captor.capture());
        assertThat(captor.getValue().getUpdateTime()).isEqualTo("2026-11-01 10:00:00");
        // 两个字段都没给 ⇒ 不改状态（goods 侧沿用原值）
        assertThat(captor.getValue().getStatus()).isNull();
        assertThat(captor.getValue().getOnSale()).isNull();

        GoodsUpdateDTO badPrice = new GoodsUpdateDTO();
        badPrice.setId(9L);
        badPrice.setMinPrice(0);
        assertThatThrownBy(() -> goodsController.update(badPrice))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("PRICE_ILLEGAL"));
    }

    /* ==================== CI-12 删除 ==================== */

    @Test
    @DisplayName("CI-12 删除透传 id（GOODS_IN_USE 由 goods 侧 409 抛出，admin 不拦截）")
    void goodsDeleteForwardsId() {
        assertThat(goodsController.delete(33L).getCode()).isEqualTo(200);
        verify(goodsClient).adminDeleteGoods(33L);
    }

    /* ==================== CN-13 库存列表 ==================== */

    @Test
    @DisplayName("CN-13 缺省按 sellable 升序、低库存/售罄开关透传；goodsStatus 非法 → 400")
    void stockListDefaultsAndFilters() {
        when(goodsClient.adminStockList(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageVO<>(0L, 0L, List.of()));

        stockController.list(null, null, "T恤", null, "1", true, false, null);
        verify(goodsClient).adminStockList(1, 20, "T恤", null, "1", true, false, "sellable");

        assertThatThrownBy(() -> stockController.list(1, 20, null, null, "7", null, null, null))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("PARAM_INVALID"));
        verify(goodsClient, never()).adminStockList(any(), any(), any(), any(), eq("7"), any(), any(), any());
    }

    /* ==================== CN-14 库存调整 ==================== */

    @Test
    @DisplayName("CN-14 number 为绝对值原样透传，出参为契约 6 字段（locked 不可用时为 null）")
    void stockUpdateForwardsAbsoluteNumber() {
        StockAdminDTO downstream = new StockAdminDTO();
        downstream.setProductId(363L);
        downstream.setNumber(7);
        downstream.setLowStock(false);
        downstream.setUpdateTime("2026-11-01 17:40:00");
        when(goodsClient.adminStockUpdate(any())).thenReturn(downstream);

        StockUpdateReqDTO body = new StockUpdateReqDTO();
        body.setProductId(363L);
        body.setNumber(7);
        body.setExpectNumber(5);
        body.setRemark("R1-05自测：库存校正");

        Result<com.litemall.domain.VO.StockUpdateVO> result = stockController.update(body);
        assertThat(result.getData().getProductId()).isEqualTo(363L);
        assertThat(result.getData().getNumber()).isEqualTo(7);
        assertThat(result.getData().getLowStock()).isFalse();
        assertThat(result.getData().getLocked()).isNull();
        assertThat(result.getData().getSellable()).isNull();

        ArgumentCaptor<StockAdminDTO> captor = ArgumentCaptor.forClass(StockAdminDTO.class);
        verify(goodsClient).adminStockUpdate(captor.capture());
        assertThat(captor.getValue().getNumber()).isEqualTo(7);
        assertThat(captor.getValue().getExpectNumber()).isEqualTo(5);
        assertThat(captor.getValue().getRemark()).isEqualTo("R1-05自测：库存校正");
    }

    @Test
    @DisplayName("CN-14 上游返回空（锁量不可用降级路径）时不抛异常，出参退化为请求值")
    void stockUpdateToleratesEmptyDownstream() {
        when(goodsClient.adminStockUpdate(any())).thenReturn(null);
        StockUpdateReqDTO body = new StockUpdateReqDTO();
        body.setProductId(1L);
        body.setNumber(3);
        Result<com.litemall.domain.VO.StockUpdateVO> result = stockController.update(body);
        assertThat(result.getData().getProductId()).isEqualTo(1L);
        assertThat(result.getData().getNumber()).isEqualTo(3);
        assertThat(result.getData().getLocked()).isNull();
    }

    @Test
    @DisplayName("CN-13 锁量不可用时不补算：goods 返回 locked=null 则 locked/sellable 均为 null")
    void stockListKeepsNullWhenLockedUnavailable() {
        StockAdminDTO row = new StockAdminDTO();
        row.setProductId(5L);
        row.setNumber(10);
        row.setLockedUnavailable(true);
        row.setLowStockThreshold(5);
        when(goodsClient.adminStockList(any(), any(), any(), any(), isNull(), any(), any(), any()))
                .thenReturn(new PageVO<>(1L, 1L, List.of(row)));

        Result<PageVO<StockAdminDTO>> result = stockController.list(1, 20, null, null, null, null, null, "sellable");
        StockAdminDTO got = result.getData().getList().get(0);
        assertThat(got.getLocked()).isNull();
        assertThat(got.getSellable()).isNull();
        assertThat(got.getLockedUnavailable()).isTrue();
        assertThat(got.getLowStockThreshold()).isEqualTo(5);
    }
}
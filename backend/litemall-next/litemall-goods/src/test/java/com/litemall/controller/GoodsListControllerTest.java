package com.litemall.controller;

import com.litemall.domain.DTO.GoodsListQuery;
import com.litemall.domain.PageVO;
import com.litemall.exception.GoodsApiException;
import com.litemall.service.IBrandService;
import com.litemall.service.ICategoryService;
import com.litemall.service.IGoodsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-31 控制器入参装配（R2-02）单元测试。
 *
 * <p>
 * C 端要求「HTTP 恒 200 + Result.code」，所以入参一律用 {@code String} 接收后手工解析：
 * 这里验证解析失败的 {@code PARAM_INVALID}、{@code sort}/{@code sortBy} 优先关系与布尔兼容写法，
 * 归一（分页夹取、区间交换、白名单落默认）由 {@code GoodsListQuery} 与集成测试覆盖。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoodsListControllerTest {

    @Mock
    private IGoodsService goodsService;

    @Mock
    private ICategoryService categoryService;

    @Mock
    private IBrandService brandService;

    private GoodsController controller;

    @BeforeEach
    void setUp() {
        controller = new GoodsController(goodsService, categoryService, brandService);
        when(goodsService.getGoodsList(any())).thenReturn(PageVO.empty(0L, 0L));
    }

    @Test
    @DisplayName("sort 优先于 sortBy 别名，两者都透传给服务层（白名单归一见 QueryNormalizeTest）")
    void sortAliases() {
        controller.getGoodsList(null, null, null, null, null, null, null, null, "price_asc", null);
        assertThat(captured().getSort()).isEqualTo("price_asc");

        controller.getGoodsList(null, null, null, null, null, null, null, "new", "price_asc", null);
        assertThat(captured().getSort()).as("sort 有值时忽略 sortBy").isEqualTo("new");
    }

    @Test
    @DisplayName("全部筛选参数原样透传；onlyStock 兼容 1/0")
    void filtersPassThrough() {
        controller.getGoodsList("2", "10", "1005002", "1001", "杯子", "5000", "10000",
                "price_desc", null, "1");
        GoodsListQuery query = captured();
        assertThat(query.getPageNum()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(10);
        assertThat(query.getCategoryId()).isEqualTo(1005002L);
        assertThat(query.getBrandId()).isEqualTo(1001L);
        assertThat(query.getKeyword()).isEqualTo("杯子");
        assertThat(query.getMinP()).isEqualTo(5000);
        assertThat(query.getMaxP()).isEqualTo(10000);
        assertThat(query.getSort()).isEqualTo("price_desc");
        assertThat(query.getOnlyStock()).isTrue();

        controller.getGoodsList(null, null, null, null, null, null, null, null, null, "0");
        assertThat(captured().getOnlyStock()).isFalse();
    }

    @Test
    @DisplayName("非数字/非法布尔一律 400 PARAM_INVALID（不允许 Spring 转成 HTTP 4xx）")
    void invalidParams() {
        assertThatThrownBy(() -> controller.getGoodsList("x", null, null, null, null, null, null,
                null, null, null))
                .isInstanceOfSatisfying(GoodsApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("PARAM_INVALID");
                    assertThat(ex.getMessage()).contains("pageNum");
                });

        assertThatThrownBy(() -> controller.getGoodsList(null, null, "abc", null, null, null, null,
                null, null, null))
                .isInstanceOfSatisfying(GoodsApiException.class,
                        ex -> assertThat(ex.getMessage()).contains("categoryId"));

        // 负数由 GoodsListQuery.normalize 拦（见 QueryNormalizeTest）；控制器只负责"非数字"
        assertThatThrownBy(() -> controller.getGoodsList(null, null, null, null, null, "abc", null,
                null, null, null))
                .isInstanceOfSatisfying(GoodsApiException.class,
                        ex -> assertThat(ex.getMessage()).contains("minP"));

        assertThatThrownBy(() -> controller.getGoodsList(null, null, null, null, null, null, null,
                null, null, "yes"))
                .isInstanceOfSatisfying(GoodsApiException.class,
                        ex -> assertThat(ex.getMessage()).contains("onlyStock"));
    }

    private GoodsListQuery captured() {
        ArgumentCaptor<GoodsListQuery> captor = ArgumentCaptor.forClass(GoodsListQuery.class);
        verify(goodsService, atLeastOnce()).getGoodsList(captor.capture());
        return captor.getValue();
    }
}
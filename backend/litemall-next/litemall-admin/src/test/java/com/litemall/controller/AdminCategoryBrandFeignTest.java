package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.domain.DTO.BrandCreateDTO;
import com.litemall.domain.DTO.BrandUpdateDTO;
import com.litemall.domain.DTO.CategoryCreateDTO;
import com.litemall.domain.DTO.CategoryUpdateDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.IdVO;
import com.litemall.domain.dto.BrandAdminDTO;
import com.litemall.domain.dto.BrandAdminVO;
import com.litemall.domain.dto.CategoryAdminDTO;
import com.litemall.domain.dto.CategoryAdminVO;
import com.litemall.domain.dto.CategoryCreateResultDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2-01：管理端分类（CN-21~24）与品牌（CN-25~28）控制器的转发行为单测。
 *
 * <p>
 * admin 侧只做「入参归一 + 转发 + 操作日志」，业务判定在 goods 侧，因此这里钉住的是：
 * </p>
 * <ul>
 *   <li>CN-21 {@code withGoodsCount} 缺省按 false，显式 true 才透传 true；</li>
 *   <li>CN-22/23/26/27 部分更新的字段原样透传（不传即 null，让 goods 侧按「不修改」处理），
 *       名称与 {@code updateTime} 去空白；</li>
 *   <li>CN-25 分页归一（页码 &lt;1 → 1，每页条数上限 100）、keyword ≤50、排序白名单外落
 *       {@code sortOrder}（不报错）、{@code isAsc} 缺省 true；</li>
 *   <li>CN-24/28 删除只透传 id；</li>
 *   <li>JSR-303：品牌/分类名为空或超长时必须被 {@code @Valid} 拦下（对外 400 {@code PARAM_INVALID}）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminCategoryBrandFeignTest {

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @Mock
    private GoodsClient goodsClient;

    private CategoryController categoryController;

    private BrandController brandController;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        categoryController = new CategoryController(goodsClient);
        brandController = new BrandController(goodsClient);
    }

    /* ==================== 分类 ==================== */

    @Test
    @DisplayName("CN-21：withGoodsCount 缺省按 false，显式 true 才查商品数")
    void categoryListPassesWithGoodsCount() {
        when(goodsClient.adminCategoryTree(false)).thenReturn(List.of());
        when(goodsClient.adminCategoryTree(true)).thenReturn(List.of());

        assertThat(categoryController.list(null).getCode()).isEqualTo(200);
        categoryController.list(true);

        verify(goodsClient).adminCategoryTree(false);
        verify(goodsClient).adminCategoryTree(true);
    }

    @Test
    @DisplayName("CN-22：pid/排序/图片原样透传，名称去空白，返回 {id, level}")
    void categoryCreateForwardsNormalizedFields() {
        when(goodsClient.adminCreateCategory(any()))
                .thenReturn(CategoryCreateResultDTO.of(88L, "2"));

        CategoryCreateDTO body = new CategoryCreateDTO();
        body.setPid(7L);
        body.setName("  上装  ");
        body.setPicUrl("  http://cdn/a.png ");
        body.setIconUrl(null);
        body.setSortOrder(5);
        body.setKeywords(" 长袖,短袖 ");
        body.setDesc("描述");

        Result<CategoryCreateResultDTO> result = categoryController.create(body);

        ArgumentCaptor<CategoryAdminDTO> captor = ArgumentCaptor.forClass(CategoryAdminDTO.class);
        verify(goodsClient).adminCreateCategory(captor.capture());
        CategoryAdminDTO sent = captor.getValue();
        assertThat(sent.getId()).isNull();
        assertThat(sent.getPid()).isEqualTo(7L);
        assertThat(sent.getName()).isEqualTo("上装");
        assertThat(sent.getPicUrl()).isEqualTo("http://cdn/a.png");
        assertThat(sent.getIconUrl()).isNull();
        assertThat(sent.getSortOrder()).isEqualTo(5);
        assertThat(sent.getKeywords()).isEqualTo("长袖,短袖");
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getId()).isEqualTo(88L);
        assertThat(result.getData().getLevel()).isEqualTo("2");
    }

    @Test
    @DisplayName("CN-23：部分更新只传 id 时其余字段为 null（goods 侧按不修改处理），updateTime 去空白")
    void categoryUpdateForwardsPartialFields() {
        CategoryUpdateDTO body = new CategoryUpdateDTO();
        body.setId(9L);
        body.setUpdateTime(" 2026-11-01 10:00:00 ");

        Result<Void> result = categoryController.update(body);

        ArgumentCaptor<CategoryAdminDTO> captor = ArgumentCaptor.forClass(CategoryAdminDTO.class);
        verify(goodsClient).adminUpdateCategory(captor.capture());
        CategoryAdminDTO sent = captor.getValue();
        assertThat(sent.getId()).isEqualTo(9L);
        assertThat(sent.getName()).isNull();
        assertThat(sent.getSortOrder()).isNull();
        assertThat(sent.getPid()).isNull();
        assertThat(sent.getUpdateTime()).isEqualTo("2026-11-01 10:00:00");
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("CN-24：删除只透传 id")
    void categoryDeleteForwardsId() {
        categoryController.delete(12L);
        verify(goodsClient).adminDeleteCategory(12L);
    }

    /* ==================== 品牌 ==================== */

    @Test
    @DisplayName("CN-25：分页归一 + keyword ≤50 + 排序白名单外落 sortOrder + isAsc 缺省 true")
    void brandListNormalizes() {
        when(goodsClient.adminListBrands(any(), any(), any(), any(), any()))
                .thenReturn(new PageVO<>((long) 0, (long) 0, List.<BrandAdminVO>of()));

        brandController.list(null, 500, "  ACME ", "bogus", null);

        verify(goodsClient).adminListBrands(1, 100, "ACME", "sortOrder", true);
    }

    @Test
    @DisplayName("CN-25：合法排序字段与 isAsc=false 原样透传")
    void brandListKeepsAllowedSort() {
        when(goodsClient.adminListBrands(any(), any(), any(), any(), any()))
                .thenReturn(new PageVO<>((long) 0, (long) 0, List.<BrandAdminVO>of()));

        brandController.list(2, 10, null, "addTime", false);

        verify(goodsClient).adminListBrands(2, 10, null, "addTime", false);
    }

    @Test
    @DisplayName("CN-26：新建品牌透传整数分 floorPrice，返回 {id}")
    void brandCreateForwardsCents() {
        when(goodsClient.adminCreateBrand(any())).thenReturn(1001L);

        BrandCreateDTO body = new BrandCreateDTO();
        body.setName(" ACME ");
        body.setFloorPrice(1999L);
        body.setSortOrder(0);
        body.setPicUrl(" http://cdn/acme.png ");

        Result<IdVO> result = brandController.create(body);

        ArgumentCaptor<BrandAdminDTO> captor = ArgumentCaptor.forClass(BrandAdminDTO.class);
        verify(goodsClient).adminCreateBrand(captor.capture());
        BrandAdminDTO sent = captor.getValue();
        assertThat(sent.getName()).isEqualTo("ACME");
        assertThat(sent.getFloorPrice()).isEqualTo(1999L);
        assertThat(sent.getPicUrl()).isEqualTo("http://cdn/acme.png");
        assertThat(result.getData().id()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("CN-27：修改品牌透传 id 与部分字段")
    void brandUpdateForwardsFields() {
        BrandUpdateDTO body = new BrandUpdateDTO();
        body.setId(1001L);
        body.setSortOrder(7);
        body.setUpdateTime("2026-11-01 11:11:11");

        brandController.update(body);

        ArgumentCaptor<BrandAdminDTO> captor = ArgumentCaptor.forClass(BrandAdminDTO.class);
        verify(goodsClient).adminUpdateBrand(captor.capture());
        BrandAdminDTO sent = captor.getValue();
        assertThat(sent.getId()).isEqualTo(1001L);
        assertThat(sent.getSortOrder()).isEqualTo(7);
        assertThat(sent.getName()).isNull();
        assertThat(sent.getFloorPrice()).isNull();
        assertThat(sent.getUpdateTime()).isEqualTo("2026-11-01 11:11:11");
    }

    @Test
    @DisplayName("CN-28：删除只透传 id")
    void brandDeleteForwardsId() {
        brandController.delete(1001L);
        verify(goodsClient).adminDeleteBrand(1001L);
    }

    /* ==================== JSR-303 ==================== */

    @Test
    @DisplayName("校验：分类名为空 / 超 32、品牌名为空 / 超 64、排序为负、update 缺 id 都被拦下")
    void validationConstraints() {
        CategoryCreateDTO blankCategory = new CategoryCreateDTO();
        assertThat(messages(blankCategory)).contains("分类名称不能为空");

        CategoryCreateDTO longCategory = new CategoryCreateDTO();
        longCategory.setName("x".repeat(33));
        assertThat(messages(longCategory)).contains("分类名称长度不能超过 32 位");

        CategoryCreateDTO negativeSort = new CategoryCreateDTO();
        negativeSort.setName("服饰");
        negativeSort.setSortOrder(-1);
        assertThat(messages(negativeSort)).contains("排序值不能小于 0");

        CategoryUpdateDTO missingId = new CategoryUpdateDTO();
        assertThat(messages(missingId)).contains("分类ID不能为空");

        BrandCreateDTO blankBrand = new BrandCreateDTO();
        assertThat(messages(blankBrand)).contains("品牌名称不能为空");

        BrandCreateDTO longBrand = new BrandCreateDTO();
        longBrand.setName("x".repeat(65));
        assertThat(messages(longBrand)).contains("品牌名称长度不能超过 64 位");

        BrandCreateDTO negativePrice = new BrandCreateDTO();
        negativePrice.setName("ACME");
        negativePrice.setFloorPrice(-1L);
        assertThat(messages(negativePrice)).contains("品牌商品最低价不能小于 0");

        BrandUpdateDTO missingBrandId = new BrandUpdateDTO();
        assertThat(messages(missingBrandId)).contains("品牌ID不能为空");
    }

    private static <T> Set<String> messages(T bean) {
        return validator.validate(bean).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
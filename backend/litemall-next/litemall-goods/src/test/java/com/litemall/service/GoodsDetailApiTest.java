package com.litemall.service;

import com.litemall.domain.PO.Brand;
import com.litemall.domain.PO.Category;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.VO.GoodsDetailVO;
import com.litemall.domain.dto.BrandAdminDTO;
import com.litemall.domain.dto.CategoryAdminDTO;
import com.litemall.mapper.BrandMapper;
import com.litemall.mapper.CategoryMapper;
import com.litemall.mapper.GoodsMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * R2-10 缺陷回归 D-D：CI-32 {@code GET /app/goods/detail} 必须带分类名/品牌名。
 *
 * <p>
 * 验收现象（qa-verify 真浏览器 + 接口双证）：详情「参数」tab 原文「品牌 —　分类 —」，
 * 接口里只有 {@code brandId/categoryId}、没有名称字段；C 端 {@code GoodsDetail.vue} 已按
 * {@code brandName/categoryName} 渲染，缺的就是后端这两个字段。
 * </p>
 *
 * <p>
 * 覆盖三件事：正常取名、{@code brandId/categoryId} 为 null 时不抛异常且字段为 null、
 * 品牌被软删后名字留 null（不返回已删品牌名）。
 * </p>
 */
@SpringBootTest
class GoodsDetailApiTest {

    /** 每次 JVM 执行唯一的名称后缀：uk_category_name / uk_brand_name 含 name */
    private static final String SUFFIX = Long.toString(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private IGoodsService goodsService;

    @Autowired
    private ICategoryService categoryService;

    @Autowired
    private IBrandService brandService;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BrandMapper brandMapper;

    private final List<Long> goodsIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> brandIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        goodsIds.forEach(id -> goodsMapper.deleteById(id));
        categoryIds.forEach(id -> categoryMapper.deleteById(id));
        brandIds.forEach(id -> brandMapper.deleteById(id));
        goodsIds.clear();
        categoryIds.clear();
        brandIds.clear();
    }

    @Test
    @DisplayName("D-D：详情返回 brandName / categoryName（不只 ID）")
    void detailCarriesNames() {
        String categoryName = "详情分类" + SUFFIX;
        String brandName = "详情品牌" + SUFFIX;
        Long categoryId = createCategory(categoryName);
        Long brandId = createBrand(brandName);
        Long goodsId = insertGoods(categoryId, brandId);

        GoodsDetailVO detail = goodsService.getGoodsDetail(goodsId);

        assertThat(detail.getCategoryId()).isEqualTo(categoryId);
        assertThat(detail.getBrandId()).isEqualTo(brandId);
        assertThat(detail.getCategoryName()).as("分类名必须返回，前端「分类 —」才会消失").isEqualTo(categoryName);
        assertThat(detail.getBrandName()).as("品牌名必须返回，前端「品牌 —」才会消失").isEqualTo(brandName);
    }

    @Test
    @DisplayName("D-D：categoryId/brandId 为 null ⇒ 不抛异常，名称为 null")
    void detailToleratesNullIds() {
        Long goodsId = insertGoods(null, null);

        GoodsDetailVO[] box = new GoodsDetailVO[1];
        assertThatCode(() -> box[0] = goodsService.getGoodsDetail(goodsId))
                .as("brandId/categoryId 为 null 时不得抛 NPE 或拼 IN ()")
                .doesNotThrowAnyException();

        GoodsDetailVO detail = box[0];
        assertThat(detail).isNotNull();
        assertThat(detail.getCategoryId()).isNull();
        assertThat(detail.getBrandId()).isNull();
        assertThat(detail.getCategoryName()).isNull();
        assertThat(detail.getBrandName()).isNull();
    }

    @Test
    @DisplayName("D-D：品牌/分类被软删 ⇒ 名称留 null（不能把已删名字展示出来）")
    void deletedCatalogLeavesNameNull() {
        Long categoryId = createCategory("详情已删分类" + SUFFIX);
        Long brandId = createBrand("详情已删品牌" + SUFFIX);
        Long goodsId = insertGoods(categoryId, brandId);

        brandMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Brand>lambdaUpdate()
                .eq(Brand::getId, brandId)
                .set(Brand::getDeleted, true));
        categoryMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Category>lambdaUpdate()
                .eq(Category::getId, categoryId)
                .set(Category::getDeleted, true));

        GoodsDetailVO detail = goodsService.getGoodsDetail(goodsId);

        assertThat(detail.getBrandName()).isNull();
        assertThat(detail.getCategoryName()).isNull();
    }

    /* ==================== 夹具 ==================== */

    private Long createCategory(String name) {
        CategoryAdminDTO dto = new CategoryAdminDTO();
        dto.setName(name);
        dto.setSortOrder(0);
        Long id = categoryService.createCategory(dto).getId();
        categoryIds.add(id);
        return id;
    }

    private Long createBrand(String name) {
        BrandAdminDTO dto = new BrandAdminDTO();
        dto.setName(name);
        dto.setSortOrder(0);
        dto.setFloorPrice(100L);
        Long id = brandService.createBrand(dto);
        brandIds.add(id);
        return id;
    }

    private Long insertGoods(Long categoryId, Long brandId) {
        Goods goods = new Goods();
        goods.setGoodsSn("R2D" + SUFFIX + "-" + System.nanoTime());
        goods.setName("详情测试商品" + SUFFIX);
        goods.setCategoryId(categoryId);
        goods.setBrandId(brandId);
        goods.setStatus(1);
        goods.setDeleted(false);
        goods.setRetailPrice(new BigDecimal("10.00"));
        goods.setCounterPrice(new BigDecimal("10.00"));
        goodsMapper.insert(goods);
        goodsIds.add(goods.getId());
        return goods.getId();
    }
}
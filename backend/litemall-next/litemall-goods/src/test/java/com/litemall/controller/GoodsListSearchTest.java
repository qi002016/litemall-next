package com.litemall.controller;

import com.litemall.domain.DTO.GoodsListQuery;
import com.litemall.domain.PO.Brand;
import com.litemall.domain.PO.Category;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.GoodsVO;
import com.litemall.mapper.BrandMapper;
import com.litemall.mapper.CategoryMapper;
import com.litemall.mapper.GoodsMapper;
import com.litemall.mapper.GoodsProductMapper;
import com.litemall.service.IGoodsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2-02 搜索筛选排序（08 v4 CI-31 {@code GET /app/goods/list}）集成测试。
 *
 * <p>
 * 直接用真实 MySQL 打服务层（与 controller 走同一入口，避免 Spring 参数解析噪声），覆盖
 * BR-SEARCH-01~07 里最容易回归的几条硬约束：
 * </p>
 * <ul>
 *   <li>只返回 {@code status=1} 且未删除商品（BR-SEARCH-01、BR-GOODS-08）；</li>
 *   <li>{@code categoryId} 传<b>一级</b>展开为子分类、传末级等值、传不存在 ⇒ 空列表不报 404；</li>
 *   <li>{@code brandId} 等值、{@code keyword} 命中名称/简介（空白不过滤）；</li>
 *   <li>价格区间按<b>全部 SKU</b> 的 min/max 与 {@code [minP,maxP]} <b>求交</b>（多规格跨界必须命中），
 *       单边过滤、{@code minP>maxP} 自动交换；</li>
 *   <li>排序：综合序（{@code sort_order ASC, id DESC}）、{@code price_asc}/{@code price_desc}（按展示价）、
 *       {@code new}（id 倒序）；</li>
 *   <li>分页：{@code total} 与筛选一致、{@code pages} 正确、逐页取完不重不漏（BR-SEARCH-06）。</li>
 * </ul>
 * <p>
 * 夹具一律带本次 JVM 唯一后缀（避免与种子数据及反复执行冲突），每个用例在 {@code finally} 里物理清理。
 * </p>
 */
@SpringBootTest
class GoodsListSearchTest {

    /** 每次 JVM 执行唯一的名称后缀：商品名/分类名带它，查询用它做隔离 */
    private static final String SUFFIX = Long.toString(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private IGoodsService goodsService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private GoodsProductMapper goodsProductMapper;

    /* ==================== 筛选维度 ==================== */

    @Test
    @DisplayName("categoryId：一级展开子分类、末级等值、不存在返回空列表（不报 404）")
    void filterByCategory() {
        String tag = "c" + SUFFIX;
        Fixture fx = seed(tag);
        try {
            PageVO<GoodsVO> rootLevel = list(query(tag).categoryId(fx.root.getId()));
            assertThat(ids(rootLevel))
                    .as("一级分类必须展开为子分类：上装(A)、下装(B) 与直接挂一级的 E 都要出现")
                    .containsExactlyInAnyOrder(fx.a.getId(), fx.b.getId(), fx.e.getId());
            assertThat(rootLevel.getTotal()).isEqualTo(3L);

            PageVO<GoodsVO> leaf = list(query(tag).categoryId(fx.child1.getId()));
            assertThat(ids(leaf))
                    .as("末级分类等值过滤，只留直接挂在它下面的 A")
                    .containsExactly(fx.a.getId());

            PageVO<GoodsVO> missing = list(query(tag).categoryId(999_999_999L));
            assertThat(missing.getList()).as("分类不存在 ⇒ 空列表，不报 404").isEmpty();
            assertThat(missing.getTotal()).isZero();
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("brandId 等值 + keyword 命中名称/简介")
    void filterByBrandAndKeyword() {
        String tag = "k" + SUFFIX;
        Fixture fx = seed(tag);
        try {
            PageVO<GoodsVO> byBrand = list(query(tag).brandId(fx.brandA.getId()));
            assertThat(ids(byBrand)).containsExactlyInAnyOrder(fx.a.getId(), fx.b.getId());

            PageVO<GoodsVO> byName = list(query(tag + "腰"));
            assertThat(ids(byName)).as("keyword 命中商品名称").containsExactly(fx.e.getId());

            PageVO<GoodsVO> byBrief = list(query(tag + "加绒"));
            assertThat(ids(byBrief)).as("keyword 命中商品简介").containsExactly(fx.b.getId());
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("价格区间：全部 SKU 的 min/max 与区间求交（多规格跨界命中）")
    void filterByPriceInterval() {
        String tag = "p" + SUFFIX;
        Fixture fx = seed(tag);
        try {
            // A 的 SKU 是 3000/5000 分：40–60 元区间内没有整段覆盖，但必须命中（求交，不是包含）
            PageVO<GoodsVO> overlap = list(query(tag).minP(4000).maxP(6000));
            assertThat(ids(overlap))
                    .as("30–50 元的商品在 40–60 区间必须出现；E=4500 命中；B=8000 不命中")
                    .containsExactlyInAnyOrder(fx.a.getId(), fx.e.getId());

            PageVO<GoodsVO> above = list(query(tag).minP(6000));
            assertThat(ids(above)).as("只填 minP ⇒ 单边过滤：A 最高 5000 < 6000 排除").containsExactly(fx.b.getId());

            PageVO<GoodsVO> below = list(query(tag).maxP(3500));
            assertThat(ids(below)).as("只填 maxP ⇒ 单边过滤：A 最低 3000 ≤ 3500 命中").containsExactly(fx.a.getId());

            PageVO<GoodsVO> swapped = list(query(tag).minP(6000).maxP(4000));
            assertThat(ids(swapped))
                    .as("minP>maxP 自动交换（BR-SEARCH-05），不报 400")
                    .containsExactlyInAnyOrder(fx.a.getId(), fx.e.getId());

            PageVO<GoodsVO> none = list(query(tag).minP(10000));
            assertThat(none.getList()).isEmpty();
        } finally {
            cleanup(fx);
        }
    }

    /* ==================== 排序 ==================== */

    @Test
    @DisplayName("排序：默认综合序、price_asc/price_desc 按展示价、new 按 id 倒序")
    void sortModes() {
        String tag = "s" + SUFFIX;
        Fixture fx = seed(tag);
        try {
            // sortOrder：E=5 < A=10 < B=20；展示价：A=3000、E=4500、B=8000
            assertThat(ids(list(query(tag))))
                    .as("默认综合序 = sort_order ASC, id DESC")
                    .containsExactly(fx.e.getId(), fx.a.getId(), fx.b.getId());

            assertThat(ids(list(query(tag).sort("price_asc"))))
                    .containsExactly(fx.a.getId(), fx.e.getId(), fx.b.getId());
            assertThat(ids(list(query(tag).sort("price_desc"))))
                    .containsExactly(fx.b.getId(), fx.e.getId(), fx.a.getId());
            assertThat(ids(list(query(tag).sort("new"))))
                    .as("new = id 倒序（自增主键与上架时序一致）")
                    .containsExactly(fx.e.getId(), fx.b.getId(), fx.a.getId());

            // 非法 sort 忽略落默认，不报 400（05 §1.4）
            assertThat(ids(list(query(tag).sort("sales_desc"))))
                    .containsExactly(fx.e.getId(), fx.a.getId(), fx.b.getId());
        } finally {
            cleanup(fx);
        }
    }

    /* ==================== 分页 ==================== */

    @Test
    @DisplayName("分页：total 与筛选一致、pages 正确、逐页取完不重不漏")
    void pagingIsStable() {
        String tag = "g" + SUFFIX;
        Fixture fx = seed(tag);
        try {
            List<Long> seen = new ArrayList<>();
            for (int pageNum = 1; pageNum <= 3; pageNum++) {
                PageVO<GoodsVO> page = list(query(tag).sort("price_asc").page(pageNum, 1));
                assertThat(page.getTotal()).isEqualTo(3L);
                assertThat(page.getPages()).isEqualTo(3L);
                assertThat(page.getList()).hasSize(1);
                seen.add(page.getList().get(0).getId());
            }
            assertThat(seen)
                    .as("翻页不重不漏（BR-SEARCH-06）")
                    .containsExactly(fx.a.getId(), fx.e.getId(), fx.b.getId());

            PageVO<GoodsVO> overflow = list(query(tag).page(4, 1));
            assertThat(overflow.getTotal()).isEqualTo(3L);
            assertThat(overflow.getList()).isEmpty();

            PageVO<GoodsVO> capped = list(query(tag).page(1, 1000));
            assertThat(capped.getTotal()).isEqualTo(3L);
            assertThat(capped.getList()).hasSize(3);
        } finally {
            cleanup(fx);
        }
    }

    /* ==================== 状态与可售 ==================== */

    @Test
    @DisplayName("只返回在售未删商品；onlyStock=true 过滤掉全部售罄商品")
    void onSaleAndOnlyStock() {
        String tag = "o" + SUFFIX;
        Fixture fx = seed(tag);
        try {
            PageVO<GoodsVO> all = list(query(tag));
            assertThat(ids(all))
                    .as("status=2（已下架）与 deleted=1 的商品都不出现在 C 端列表")
                    .doesNotContain(fx.offSale.getId(), fx.deleted.getId())
                    .containsExactlyInAnyOrder(fx.a.getId(), fx.b.getId(), fx.e.getId());

            Goods soldOut = insertGoods(fx, fx.child1.getId(), fx.brandB.getId(),
                    tag + "售罄", 60, 1, false, "无库存");
            insertSku(fx, soldOut.getId(), "70.00", 0);
            try {
                PageVO<GoodsVO> allWithSoldOut = list(query(tag));
                assertThat(ids(allWithSoldOut)).contains(soldOut.getId());

                PageVO<GoodsVO> inStock = list(query(tag).onlyStock(true));
                assertThat(ids(inStock))
                        .as("onlyStock=true ⇒ 总可售 > 0")
                        .doesNotContain(soldOut.getId())
                        .containsExactlyInAnyOrder(fx.a.getId(), fx.b.getId(), fx.e.getId());
            } finally {
                deleteGoods(List.of(soldOut.getId()));
            }
        } finally {
            cleanup(fx);
        }
    }

    /* ==================== 夹具 ==================== */

    private QueryBuilder query(String keyword) {
        return new QueryBuilder(keyword);
    }

    /** 极简查询构造器，避免每个用例重复 setter 噪音 */
    private static final class QueryBuilder {

        private final GoodsListQuery query = new GoodsListQuery();

        QueryBuilder(String keyword) {
            query.setKeyword(keyword);
        }

        QueryBuilder categoryId(Long value) {
            query.setCategoryId(value);
            return this;
        }

        QueryBuilder brandId(Long value) {
            query.setBrandId(value);
            return this;
        }

        QueryBuilder minP(Integer value) {
            query.setMinP(value);
            return this;
        }

        QueryBuilder maxP(Integer value) {
            query.setMaxP(value);
            return this;
        }

        QueryBuilder sort(String value) {
            query.setSort(value);
            return this;
        }

        QueryBuilder onlyStock(boolean value) {
            query.setOnlyStock(value);
            return this;
        }

        QueryBuilder page(int pageNum, int pageSize) {
            query.setPageNum(pageNum);
            query.setPageSize(pageSize);
            return this;
        }

        GoodsListQuery build() {
            return query;
        }
    }

    private PageVO<GoodsVO> list(QueryBuilder builder) {
        return goodsService.getGoodsList(builder.build());
    }

    private static List<Long> ids(PageVO<GoodsVO> page) {
        return page.getList().stream().map(GoodsVO::getId).toList();
    }

    /** 一个用例一套隔离夹具：一级分类 + 两个二级分类 + 两个品牌 + 5 个商品 */
    private Fixture seed(String tag) {
        Fixture fx = new Fixture();
        fx.root = insertCategory(0L, tag + "服饰");
        fx.child1 = insertCategory(fx.root.getId(), tag + "上装");
        fx.child2 = insertCategory(fx.root.getId(), tag + "下装");
        fx.brandA = insertBrand(tag + "ACME");
        fx.brandB = insertBrand(tag + "BETA");

        // A：上装 / ACME，SKU 30.00 与 50.00（多规格跨界，展示价 3000）
        fx.a = insertGoods(fx, fx.child1.getId(), fx.brandA.getId(), tag + "T恤", 10, 1, false, "纯棉短袖");
        insertSku(fx, fx.a.getId(), "30.00", 5);
        insertSku(fx, fx.a.getId(), "50.00", 0);
        // B：下装 / ACME，SKU 80.00，简介命中 keyword
        fx.b = insertGoods(fx, fx.child2.getId(), fx.brandA.getId(), tag + "卫衣", 20, 1, false, tag + "加绒连帽");
        insertSku(fx, fx.b.getId(), "80.00", 3);
        // E：直接挂一级分类 / BETA，SKU 45.00
        fx.e = insertGoods(fx, fx.root.getId(), fx.brandB.getId(), tag + "腰带", 5, 1, false, "牛皮");
        insertSku(fx, fx.e.getId(), "45.00", 2);
        // 已下架 / 已删除：C 端都不可见
        fx.offSale = insertGoods(fx, fx.child1.getId(), fx.brandA.getId(), tag + "清仓", 30, 2, false, "下架");
        insertSku(fx, fx.offSale.getId(), "10.00", 9);
        fx.deleted = insertGoods(fx, fx.child1.getId(), fx.brandA.getId(), tag + "已删", 40, 1, true, "已删除");
        insertSku(fx, fx.deleted.getId(), "10.00", 9);
        return fx;
    }

    private Category insertCategory(Long pid, String name) {
        Category category = new Category();
        category.setPid(pid == null ? 0L : pid);
        category.setName(name);
        category.setLevel(pid == null || pid == 0L ? "1" : "2");
        category.setSortOrder(50);
        category.setKeywords("");
        category.setIconUrl("");
        category.setPicUrl("");
        category.setDeleted(false);
        categoryMapper.insert(category);
        return category;
    }

    private Brand insertBrand(String name) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setDesc("");
        brand.setPicUrl("");
        brand.setSortOrder(50);
        brand.setFloorPrice(BigDecimal.ZERO);
        brand.setDeleted(false);
        brandMapper.insert(brand);
        return brand;
    }

    private Goods insertGoods(Fixture fx, Long categoryId, Long brandId, String name,
                              int sortOrder, int status, boolean deleted, String brief) {
        Goods goods = new Goods();
        goods.setGoodsSn("R2SEARCH" + SUFFIX + "-" + System.nanoTime());
        goods.setName(name);
        goods.setBrief(brief);
        goods.setCategoryId(categoryId);
        goods.setBrandId(brandId);
        goods.setSortOrder(sortOrder);
        goods.setStatus(status);
        goods.setDeleted(deleted);
        goodsMapper.insert(goods);
        fx.goodsIds.add(goods.getId());
        return goods;
    }

    private void insertSku(Fixture fx, Long goodsId, String price, int number) {
        GoodsProduct sku = new GoodsProduct();
        sku.setGoodsId(goodsId);
        sku.setPrice(new BigDecimal(price));
        sku.setNumber(number);
        sku.setSkuCode("R2S-" + goodsId + "-" + System.nanoTime());
        // uk_goods_product_combo(goods_id, spec_value_ids)：同一商品的 SKU 必须给出不同规格组合
        sku.setSpecValueIds(String.valueOf(++fx.skuSeq));
        sku.setSpecifications("[]");
        sku.setDeleted(false);
        goodsProductMapper.insert(sku);
        fx.skuIds.add(sku.getId());
    }

    private void cleanup(Fixture fx) {
        deleteGoods(fx.goodsIds);
        if (fx.brandA != null) {
            brandMapper.deleteById(fx.brandA.getId());
        }
        if (fx.brandB != null) {
            brandMapper.deleteById(fx.brandB.getId());
        }
        if (fx.child1 != null) {
            categoryMapper.deleteById(fx.child1.getId());
        }
        if (fx.child2 != null) {
            categoryMapper.deleteById(fx.child2.getId());
        }
        if (fx.root != null) {
            categoryMapper.deleteById(fx.root.getId());
        }
    }

    private void deleteGoods(List<Long> goodsIds) {
        for (Long goodsId : goodsIds) {
            goodsProductMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GoodsProduct>()
                    .eq("goods_id", goodsId));
            goodsMapper.deleteById(goodsId);
        }
    }

    /** 单用例夹具（含已建行 ID，便于物理清理） */
    private static final class Fixture {
        private Category root;
        private Category child1;
        private Category child2;
        private Brand brandA;
        private Brand brandB;
        private Goods a;
        private Goods b;
        private Goods e;
        private Goods offSale;
        private Goods deleted;
        private final List<Long> goodsIds = new ArrayList<>();
        private final List<Long> skuIds = new ArrayList<>();
        /** 规格值串自增（uk_goods_product_combo 需要同商品内唯一） */
        private int skuSeq;
    }
}
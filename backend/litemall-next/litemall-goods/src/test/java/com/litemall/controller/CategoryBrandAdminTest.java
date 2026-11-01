package com.litemall.controller;

import com.litemall.constant.GoodsErrorCode;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.BrandAdminDTO;
import com.litemall.domain.dto.BrandAdminVO;
import com.litemall.domain.dto.CategoryAdminDTO;
import com.litemall.domain.dto.CategoryAdminVO;
import com.litemall.domain.dto.CategoryCreateResultDTO;
import com.litemall.exception.GoodsApiException;
import com.litemall.mapper.BrandMapper;
import com.litemall.mapper.CategoryMapper;
import com.litemall.mapper.GoodsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * R2-01 分类 / 品牌（CN-21~28，对内 {@code /inner/goods/admin/category|brand/*}）集成测试。
 *
 * <p>
 * 直接用真实 MySQL 打 {@link GoodsInnerController}（与 admin 经 Feign 走的是同一批方法），
 * 覆盖 05（FROZEN）里最容易回归的几条硬约束：
 * </p>
 * <ul>
 *   <li>两级树：{@code children} 最多两层，{@code level} 为 {@code "1"/"2"}，
 *       {@code goodsCount} 含已下架商品；</li>
 *   <li>三级分类 ⇒ 400 {@code PARAM_INVALID} +「分类最多两级」（05 §1.2 D：不单列错误码）；</li>
 *   <li>同级重名 ⇒ 409 {@code CATEGORY_NAME_DUPLICATED}；品牌重名 ⇒ 409 {@code BRAND_NAME_DUPLICATED}；</li>
 *   <li>删除：有子分类 ⇒ {@code CATEGORY_HAS_CHILDREN}；有商品（含已下架）⇒
 *       {@code CATEGORY_IN_USE}/{@code BRAND_IN_USE} 且 msg 逐字「请先移走商品」，行仍存在；</li>
 *   <li>删除成功 ⇒ <b>物理删除</b>（{@code selectById} 为空，05 §5.3 A）；</li>
 *   <li>分类改父级 ⇒ 400；CAS 失配 ⇒ 409 {@code CONCURRENT_MODIFIED}。</li>
 * </ul>
 * <p>
 * 夹具一律带唯一后缀（避免与种子数据、以及反复执行撞 {@code uk_category_name} / {@code uk_brand_name}），
 * 每个用例结束在 {@code finally} 里物理清理，包括它建出来的商品行。
 * </p>
 */
@SpringBootTest
class CategoryBrandAdminTest {

    /** 每次 JVM 执行唯一的名称后缀：唯一键含 name，反复跑不能互相干扰 */
    private static final String SUFFIX = Long.toString(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private GoodsInnerController goodsInnerController;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    /* ==================== CN-21 分类树 ==================== */

    @Test
    @DisplayName("CN-21：两级分类树 + level 字符串 + goodsCount（含已下架）")
    void categoryTree_twoLevelsWithGoodsCount() {
        String rootName = "R2测试服饰" + SUFFIX;
        CategoryCreateResultDTO root = createCategory(null, rootName);
        CategoryCreateResultDTO child = createCategory(root.getId(), "上装");
        Goods offSale = null;
        try {
            assertThat(root.getLevel()).isEqualTo("1");
            assertThat(child.getLevel()).isEqualTo("2");

            offSale = insertGoods(root.getId(), null, 2);

            // withGoodsCount=false ⇒ goodsCount 为 null（契约：不传则不查）
            List<CategoryAdminVO> plain = goodsInnerController.adminCategoryList(false);
            CategoryAdminVO rootNode = findById(plain, root.getId());
            assertThat(rootNode).as("新建的分类必须立刻出现在树里（管理端不走缓存）").isNotNull();
            assertThat(rootNode.getLevel()).isEqualTo("1");
            assertThat(rootNode.getGoodsCount()).isNull();
            assertThat(rootNode.getChildren()).hasSize(1);
            assertThat(rootNode.getChildren().get(0).getId()).isEqualTo(child.getId());
            assertThat(rootNode.getChildren().get(0).getLevel()).isEqualTo("2");
            assertThat(rootNode.getChildren().get(0).getChildren()).isEmpty();

            // withGoodsCount=true ⇒ 含已下架（status=2）商品
            List<CategoryAdminVO> counted = goodsInnerController.adminCategoryList(true);
            assertThat(findById(counted, root.getId()).getGoodsCount()).isEqualTo("1");
            assertThat(findById(counted, root.getId()).getChildren().get(0).getGoodsCount())
                    .isEqualTo("0");
        } finally {
            if (offSale != null) {
                goodsMapper.deleteById(offSale.getId());
            }
            hardDeleteCategory(child.getId());
            hardDeleteCategory(root.getId());
        }
    }

    @Test
    @DisplayName("CN-22：父分类是二级 ⇒ 400 PARAM_INVALID +「分类最多两级」")
    void createCategory_rejectsThirdLevel() {
        CategoryCreateResultDTO root = createCategory(null, "R2测试三级" + SUFFIX);
        CategoryCreateResultDTO child = createCategory(root.getId(), "二级");
        try {
            CategoryAdminDTO dto = new CategoryAdminDTO();
            dto.setPid(child.getId());
            dto.setName("三级");
            GoodsApiException e = assertThrows(GoodsApiException.class,
                    () -> goodsInnerController.adminCategoryCreate(dto));
            assertThat(e.getErrorCode()).isEqualTo(GoodsErrorCode.PARAM_INVALID);
            assertThat(e.getMessage()).isEqualTo("分类最多两级");
        } finally {
            hardDeleteCategory(child.getId());
            hardDeleteCategory(root.getId());
        }
    }

    @Test
    @DisplayName("CN-22：父分类不存在 ⇒ 404 RESOURCE_NOT_FOUND「父分类不存在」")
    void createCategory_parentMissing() {
        CategoryAdminDTO dto = new CategoryAdminDTO();
        dto.setPid(987_654_321L);
        dto.setName("孤儿分类" + SUFFIX);
        GoodsApiException e = assertThrows(GoodsApiException.class,
                () -> goodsInnerController.adminCategoryCreate(dto));
        assertThat(e.getErrorCode()).isEqualTo(GoodsErrorCode.RESOURCE_NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo("父分类不存在");
    }

    @Test
    @DisplayName("CN-22：同级重名 ⇒ 409 CATEGORY_NAME_DUPLICATED")
    void createCategory_duplicateNameSameParent() {
        String rootName = "R2测试重名" + SUFFIX;
        CategoryCreateResultDTO root = createCategory(null, rootName);
        try {
            CategoryAdminDTO dto = new CategoryAdminDTO();
            dto.setName(rootName);
            GoodsApiException e = assertThrows(GoodsApiException.class,
                    () -> goodsInnerController.adminCategoryCreate(dto));
            assertThat(e.getErrorCode()).isEqualTo(GoodsErrorCode.CATEGORY_NAME_DUPLICATED);
            assertThat(e.getMessage()).isEqualTo("同级下已存在同名分类");
        } finally {
            hardDeleteCategory(root.getId());
        }
    }

    /* ==================== CN-23 修改 ==================== */

    @Test
    @DisplayName("CN-23：改父级 ⇒ 400「本期不支持调整上级分类」；CAS 失配 ⇒ 409 CONCURRENT_MODIFIED")
    void updateCategory_pidFrozenAndCasConflict() {
        CategoryCreateResultDTO rootA = createCategory(null, "R2测试改父A" + SUFFIX);
        CategoryCreateResultDTO rootB = createCategory(null, "R2测试改父B" + SUFFIX);
        try {
            CategoryAdminDTO moveDto = new CategoryAdminDTO();
            moveDto.setId(rootA.getId());
            moveDto.setPid(rootB.getId());
            GoodsApiException move = assertThrows(GoodsApiException.class,
                    () -> goodsInnerController.adminCategoryUpdate(moveDto));
            assertThat(move.getErrorCode()).isEqualTo(GoodsErrorCode.PARAM_INVALID);
            assertThat(move.getMessage()).isEqualTo("本期不支持调整上级分类");

            CategoryAdminDTO staleDto = new CategoryAdminDTO();
            staleDto.setId(rootA.getId());
            staleDto.setName("改名后" + SUFFIX);
            staleDto.setUpdateTime("2000-01-01 00:00:00");
            GoodsApiException cas = assertThrows(GoodsApiException.class,
                    () -> goodsInnerController.adminCategoryUpdate(staleDto));
            assertThat(cas.getErrorCode()).isEqualTo(GoodsErrorCode.CONCURRENT_MODIFIED);
            assertThat(cas.getMessage()).isEqualTo("内容已被他人修改，请刷新后重试");
        } finally {
            hardDeleteCategory(rootA.getId());
            hardDeleteCategory(rootB.getId());
        }
    }

    /* ==================== CN-24 删除 ==================== */

    @Test
    @DisplayName("CN-24：有子分类 ⇒ 409 CATEGORY_HAS_CHILDREN，父分类仍在")
    void deleteCategory_withChildren() {
        CategoryCreateResultDTO root = createCategory(null, "R2测试有子" + SUFFIX);
        CategoryCreateResultDTO child = createCategory(root.getId(), "子");
        try {
            GoodsApiException e = assertThrows(GoodsApiException.class,
                    () -> goodsInnerController.adminCategoryDelete(root.getId()));
            assertThat(e.getErrorCode()).isEqualTo(GoodsErrorCode.CATEGORY_HAS_CHILDREN);
            assertThat(categoryMapper.selectById(root.getId())).isNotNull();
        } finally {
            hardDeleteCategory(child.getId());
            hardDeleteCategory(root.getId());
        }
    }

    @Test
    @DisplayName("CN-24：分类下有已下架商品 ⇒ 409 CATEGORY_IN_USE +「请先移走商品」，分类仍在")
    void deleteCategory_inUse() {
        CategoryCreateResultDTO root = createCategory(null, "R2测试占用" + SUFFIX);
        Goods offSale = insertGoods(root.getId(), null, 2);
        try {
            GoodsApiException e = assertThrows(GoodsApiException.class,
                    () -> goodsInnerController.adminCategoryDelete(root.getId()));
            assertThat(e.getErrorCode()).isEqualTo(GoodsErrorCode.CATEGORY_IN_USE);
            assertThat(e.getMessage()).isEqualTo("请先移走商品");
            assertThat(categoryMapper.selectById(root.getId())).isNotNull();
        } finally {
            goodsMapper.deleteById(offSale.getId());
            hardDeleteCategory(root.getId());
        }
    }

    @Test
    @DisplayName("CN-24：无占用 ⇒ 物理删除（selectById 为空）")
    void deleteCategory_physical() {
        CategoryCreateResultDTO root = createCategory(null, "R2测试物理删" + SUFFIX);
        goodsInnerController.adminCategoryDelete(root.getId());
        assertNull(categoryMapper.selectById(root.getId()));
    }

    /* ==================== CN-25~28 品牌 ==================== */

    @Test
    @DisplayName("CN-25/26：新建品牌 → 列表可查；floorPrice 分↔元；goodsCount 字符串")
    void brandCreateAndList() {
        String name = "ACME" + SUFFIX;
        BrandAdminDTO dto = new BrandAdminDTO();
        dto.setName(name);
        dto.setDesc("测试品牌");
        dto.setPicUrl("http://cdn.test/acme.png");
        dto.setSortOrder(3);
        dto.setFloorPrice(1999L);
        Long id = goodsInnerController.adminBrandCreate(dto);
        try {
            assertNotNull(id);
            // 库内是 DECIMAL(10,2) 元
            assertThat(brandMapper.selectById(id).getFloorPrice()).isEqualByComparingTo("19.99");

            PageVO<BrandAdminVO> page = goodsInnerController.adminBrandList(1, 100, "ACME" + SUFFIX,
                    "sortOrder", true);
            assertThat(page.getList()).hasSize(1);
            BrandAdminVO vo = page.getList().get(0);
            assertThat(vo.getId()).isEqualTo(id);
            assertThat(vo.getName()).isEqualTo(name);
            assertThat(vo.getFloorPrice()).isEqualTo(1999L);
            assertThat(vo.getGoodsCount()).isEqualTo("0");
            assertThat(vo.getAddTime()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        } finally {
            brandMapper.deleteById(id);
        }
    }

    @Test
    @DisplayName("CN-26：品牌重名 ⇒ 409 BRAND_NAME_DUPLICATED「品牌名已存在」")
    void createBrand_duplicateName() {
        String name = "DUP" + SUFFIX;
        BrandAdminDTO dto = new BrandAdminDTO();
        dto.setName(name);
        Long id = goodsInnerController.adminBrandCreate(dto);
        try {
            BrandAdminDTO again = new BrandAdminDTO();
            again.setName(name);
            GoodsApiException e = assertThrows(GoodsApiException.class,
                    () -> goodsInnerController.adminBrandCreate(again));
            assertThat(e.getErrorCode()).isEqualTo(GoodsErrorCode.BRAND_NAME_DUPLICATED);
            assertThat(e.getMessage()).isEqualTo("品牌名已存在");
        } finally {
            brandMapper.deleteById(id);
        }
    }

    @Test
    @DisplayName("CN-28：品牌下有商品 ⇒ 409 BRAND_IN_USE +「请先移走商品」；清空后物理删除")
    void deleteBrand_inUseThenPhysical() {
        BrandAdminDTO dto = new BrandAdminDTO();
        dto.setName("USED" + SUFFIX);
        Long id = goodsInnerController.adminBrandCreate(dto);
        Goods goods = insertGoods(null, id, 2);
        try {
            GoodsApiException e = assertThrows(GoodsApiException.class,
                    () -> goodsInnerController.adminBrandDelete(id));
            assertThat(e.getErrorCode()).isEqualTo(GoodsErrorCode.BRAND_IN_USE);
            assertThat(e.getMessage()).isEqualTo("请先移走商品");
            assertThat(brandMapper.selectById(id)).isNotNull();

            goodsMapper.deleteById(goods.getId());
            goods = null;
            goodsInnerController.adminBrandDelete(id);
            assertNull(brandMapper.selectById(id));
        } finally {
            if (goods != null) {
                goodsMapper.deleteById(goods.getId());
            }
            brandMapper.deleteById(id);
        }
    }

    /* ==================== 夹具 ==================== */

    private CategoryCreateResultDTO createCategory(Long pid, String name) {
        CategoryAdminDTO dto = new CategoryAdminDTO();
        dto.setPid(pid);
        dto.setName(name);
        return goodsInnerController.adminCategoryCreate(dto);
    }

    /** 物理删除分类（绕过逻辑删除，保证反复执行不撞唯一键） */
    private void hardDeleteCategory(Long id) {
        if (id != null) {
            categoryMapper.deleteById(id);
        }
    }

    /**
     * 插入一条商品夹具。
     *
     * @param categoryId 分类ID，可空
     * @param brandId    品牌ID，可空
     * @param status     商品状态（0 草稿 / 1 在售 / 2 已下架）
     * @return 已落库的商品
     */
    private Goods insertGoods(Long categoryId, Long brandId, int status) {
        Goods goods = new Goods();
        goods.setGoodsSn("R2TEST" + SUFFIX + "-" + System.nanoTime());
        goods.setName("R2测试商品" + SUFFIX);
        goods.setCategoryId(categoryId);
        goods.setBrandId(brandId);
        goods.setStatus(status);
        goods.setDeleted(false);
        goodsMapper.insert(goods);
        return goods;
    }

    private static CategoryAdminVO findById(List<CategoryAdminVO> tree, Long id) {
        return tree.stream()
                .filter(node -> node.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
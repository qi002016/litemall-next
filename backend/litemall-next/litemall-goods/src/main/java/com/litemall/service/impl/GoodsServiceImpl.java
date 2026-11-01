package com.litemall.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.DTO.GoodsAdminQuery;
import com.litemall.domain.DTO.GoodsListQuery;
import com.litemall.domain.PO.Brand;
import com.litemall.domain.PO.Category;
import com.litemall.domain.PO.Comment;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsAttribute;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.PO.GoodsSpecification;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.GoodsDetailVO;
import com.litemall.domain.VO.GoodsVO;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.exception.GoodsApiException;
import com.litemall.mapper.GoodsMapper;
import com.litemall.service.IBrandService;
import com.litemall.service.ICategoryService;
import com.litemall.service.ICommentService;
import com.litemall.service.IGoodsAttributeService;
import com.litemall.service.IGoodsProductService;
import com.litemall.service.IGoodsService;
import com.litemall.service.IGoodsSpecificationService;
import com.litemall.support.GoodsSupport;
import com.litemall.support.LockedStockProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品读路径（C 端列表/详情、管理端列表）+ 管理端商品数统计。
 * </p>
 *
 * <p>
 * 三量口径见 08 §2.1：{@code number} 为唯一持久化库存，{@code locked} 由 order 域 CN-IN-04 派生，
 * {@code sellable = max(0, number − locked)}。因此列表/详情**不做详情缓存**（sellable 随时变化，
 * 缓存会让 C 端看到过期的"可售"）。
 * </p>
 *
 * <p>
 * 读路径统一分两步：先按筛选条件取商品骨架，再批量取 SKU 并一次性汇总 locked，
 * 避免 N+1 与逐商品 RPC（08 §2.1 要求批量化）。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Service
@RequiredArgsConstructor
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements IGoodsService {

    /** 批量查询 chunk（避免 IN 过长） */
    private static final int IN_CHUNK = 500;

    private final IGoodsProductService goodsProductService;
    private final IGoodsSpecificationService specificationService;
    private final IGoodsAttributeService attributeService;
    private final ICommentService commentService;
    private final ICategoryService categoryService;
    private final IBrandService brandService;
    private final LockedStockProvider lockedStockProvider;

    /* ==================== C 端：CI-31 列表 ==================== */

    @Override
    public PageVO<GoodsVO> getGoodsList(GoodsListQuery query) {
        query.normalize();
        // R2-02：categoryId 兼容一级分类（展开为自身 + 二级子分类）；不存在/已删 ⇒ 空页，不报 404
        List<Long> categoryScope = null;
        if (query.getCategoryId() != null) {
            categoryScope = categoryService.categoryScopeIds(query.getCategoryId());
            if (categoryScope.isEmpty()) {
                return PageVO.empty(0L, 0L);
            }
        }
        List<Goods> goodsList = lambdaQuery()
                .eq(Goods::getStatus, 1)
                .eq(Goods::getDeleted, false)
                .in(categoryScope != null, Goods::getCategoryId, categoryScope)
                .eq(query.getBrandId() != null, Goods::getBrandId, query.getBrandId())
                .and(query.getKeyword() != null, w -> w
                        .like(Goods::getName, query.getKeyword())
                        .or().like(Goods::getBrief, query.getKeyword())
                        .or().like(Goods::getKeywords, query.getKeyword()))
                .orderByAsc(Goods::getSortOrder)
                .orderByDesc(Goods::getAddTime)
                // BR-SEARCH-06：确定次序键，翻页不重不漏（综合序同值时 id 兜底）
                .orderByDesc(Goods::getId)
                .list();
        if (goodsList.isEmpty()) {
            return PageVO.empty(0L, 0L);
        }

        Map<Long, List<GoodsProduct>> productMap = loadProducts(
                goodsList.stream().map(Goods::getId).toList());
        Set<Long> productIds = productMap.values().stream()
                .flatMap(List::stream)
                .map(GoodsProduct::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LockedStockProvider.LockedStockQuery locked = lockedStockProvider.query(productIds);

        List<GoodsVO> rows = new ArrayList<>(goodsList.size());
        for (Goods goods : goodsList) {
            List<GoodsProduct> skus = productMap.get(goods.getId());
            if (!matchPrice(goods, skus, query)) {
                continue;
            }
            GoodsVO row = buildListRow(goods, skus, locked);
            if (Boolean.TRUE.equals(query.getOnlyStock())) {
                if (row.getTotalStock() == null || row.getTotalStock() <= 0) {
                    continue;
                }
            }
            rows.add(row);
        }
        sortRows(rows, query.getSort());
        return page(rows, query.getPageNum(), query.getPageSize());
    }

    /**
     * 组装列表行：minPrice（含无货 SKU 最低价）、displayPrice（有可售 SKU 最低价）、totalStock（Σ sellable）。
     */
    private GoodsVO buildListRow(Goods goods, List<GoodsProduct> products,
                                 LockedStockProvider.LockedStockQuery locked) {
        List<GoodsProduct> skus = products == null ? List.of() : products;
        boolean lockedUnavailable = !locked.available();
        Integer minFen = null;
        Integer displayFen = null;
        int totalSellable = 0;
        for (GoodsProduct sku : skus) {
            Integer fen = GoodsSupport.yuanToFen(sku.getPrice());
            if (fen != null) {
                minFen = minFen == null ? fen : Math.min(minFen, fen);
            }
            int sellable = effectiveSellable(sku, locked);
            totalSellable += sellable;
            if (sellable > 0 && fen != null) {
                displayFen = displayFen == null ? fen : Math.min(displayFen, fen);
            }
        }
        // 无 SKU（历史脏数据）时退回商品级价格，保证列表不出现 null 价
        if (minFen == null) {
            minFen = GoodsSupport.yuanToFen(goods.getRetailPrice() != null
                    ? goods.getRetailPrice() : goods.getCounterPrice());
        }

        GoodsVO row = new GoodsVO();
        row.setId(goods.getId());
        row.setName(goods.getName());
        row.setBrief(goods.getBrief());
        row.setPicUrl(goods.getPicUrl());
        row.setCategoryId(goods.getCategoryId());
        row.setBrandId(goods.getBrandId());
        row.setMinPrice(minFen);
        row.setDisplayPrice(displayFen != null ? displayFen : minFen);
        row.setTotalStock(totalSellable);
        row.setSoldOut(totalSellable == 0);
        row.setLockedUnavailable(lockedUnavailable);
        return row;
    }

    /**
     * 生效可售量：locked 可用 ⇒ max(0, number−locked)；不可用 ⇒ 降级为 number（并置 lockedUnavailable，
     * 前端据此提示"库存信息同步中"），不把未知当 0。CN-13 库存列表则严格返回 null（见 StockServiceImpl）。
     */
    private int effectiveSellable(GoodsProduct sku, LockedStockProvider.LockedStockQuery locked) {
        Integer number = sku.getNumber();
        int stock = number == null ? 0 : number;
        Integer sellable = GoodsSupport.sellable(number, locked.lockedOf(sku.getId()), !locked.available());
        return sellable == null ? stock : sellable;
    }

    /**
     * 价格区间过滤（R2-02 / BR-SEARCH-05）：取该商品<b>全部 SKU</b> 的最低价与最高价（分），
     * 与 {@code [minP, maxP]} 求交——多规格商品只要存在落在区间内的规格即命中
     * （例：30–50 元的商品在 40–60 元区间内必须出现，即使它的最低价/展示价都在区间外）。
     *
     * <p>
     * 无 SKU 的历史脏数据退回商品级 {@code retail_price}/{@code counter_price}；
     * 完全没有价格信息时，有区间约束即排除（不猜价格）。
     * </p>
     */
    private boolean matchPrice(Goods goods, List<GoodsProduct> products, GoodsListQuery query) {
        if (query.getMinP() == null && query.getMaxP() == null) {
            return true;
        }
        Integer lowFen = null;
        Integer highFen = null;
        if (products != null) {
            for (GoodsProduct sku : products) {
                Integer fen = GoodsSupport.yuanToFen(sku.getPrice());
                if (fen == null) {
                    continue;
                }
                lowFen = lowFen == null ? fen : Math.min(lowFen, fen);
                highFen = highFen == null ? fen : Math.max(highFen, fen);
            }
        }
        if (lowFen == null) {
            Integer fallback = GoodsSupport.yuanToFen(goods.getRetailPrice() != null
                    ? goods.getRetailPrice() : goods.getCounterPrice());
            lowFen = fallback;
            highFen = fallback;
        }
        if (lowFen == null) {
            return false;
        }
        if (query.getMinP() != null && highFen < query.getMinP()) {
            return false;
        }
        return query.getMaxP() == null || lowFen <= query.getMaxP();
    }

    private void sortRows(List<GoodsVO> rows, String sort) {
        if (sort == null) {
            // 综合排序：SQL 已按 sort_order asc, add_time desc, id desc 返回，保持稳定
            return;
        }
        // BR-SEARCH-06：每个排序都必须带唯一确定的次序键（id 倒序），否则同价商品翻页会重/漏
        Comparator<GoodsVO> byIdDesc = Comparator.comparing(GoodsVO::getId,
                Comparator.nullsLast(Comparator.reverseOrder()));
        if ("new".equals(sort)) {
            rows.sort(byIdDesc);
            return;
        }
        // 价格排序按「展示价」（08 §1.1 BR-SEARCH-04）
        Comparator<GoodsVO> byPrice = Comparator.comparing(
                GoodsVO::getDisplayPrice, Comparator.nullsLast(Comparator.naturalOrder()));
        if ("price_asc".equals(sort)) {
            rows.sort(byPrice.thenComparing(byIdDesc));
        } else if ("price_desc".equals(sort)) {
            // 用 nullsLast + 反序（而不是 byPrice.reversed()），保证无价商品仍排在最后
            Comparator<GoodsVO> byPriceDesc = Comparator.comparing(GoodsVO::getDisplayPrice,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            rows.sort(byPriceDesc.thenComparing(byIdDesc));
        }
    }

    /* ==================== C 端：CI-32 详情 ==================== */

    @Override
    public GoodsDetailVO getGoodsDetail(Long goodsId) {
        Goods goods = getById(goodsId);
        if (goods == null || Boolean.TRUE.equals(goods.getDeleted())) {
            throw GoodsApiException.notFound("商品不存在");
        }
        if (!Integer.valueOf(1).equals(goods.getStatus())) {
            // 草稿(0)/已下架(2) 对 C 端一律按"已下架"处理（BR-GOODS-08）
            throw GoodsApiException.goodsOffSale();
        }

        List<GoodsProduct> skus = goodsProductService.lambdaQuery()
                .eq(GoodsProduct::getGoodsId, goodsId)
                .eq(GoodsProduct::getDeleted, false)
                .orderByAsc(GoodsProduct::getId)
                .list();
        List<GoodsSpecification> specifications = specificationService.lambdaQuery()
                .eq(GoodsSpecification::getGoodsId, goodsId)
                .eq(GoodsSpecification::getDeleted, false)
                .orderByAsc(GoodsSpecification::getId)
                .list();
        List<GoodsAttribute> attributes = attributeService.lambdaQuery()
                .eq(GoodsAttribute::getGoodsId, goodsId)
                .eq(GoodsAttribute::getDeleted, false)
                .orderByAsc(GoodsAttribute::getId)
                .list();

        Set<Long> productIds = skus.stream().map(GoodsProduct::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LockedStockProvider.LockedStockQuery locked = lockedStockProvider.query(productIds);
        boolean lockedUnavailable = !locked.available();

        // 维度树：按 specification 分组，保留首次出现顺序（= 建品时的维度顺序）
        LinkedHashMap<String, List<GoodsSpecification>> dims = new LinkedHashMap<>();
        Map<Long, GoodsSpecification> specById = new HashMap<>();
        for (GoodsSpecification spec : specifications) {
            dims.computeIfAbsent(spec.getSpecification(), k -> new ArrayList<>()).add(spec);
            specById.put(spec.getId(), spec);
        }

        List<GoodsDetailVO.SpecVO> specVOs = new ArrayList<>(dims.size());
        for (Map.Entry<String, List<GoodsSpecification>> entry : dims.entrySet()) {
            GoodsDetailVO.SpecVO specVO = new GoodsDetailVO.SpecVO();
            specVO.setName(entry.getKey());
            List<GoodsDetailVO.SpecValueVO> values = new ArrayList<>(entry.getValue().size());
            for (GoodsSpecification spec : entry.getValue()) {
                GoodsDetailVO.SpecValueVO valueVO = new GoodsDetailVO.SpecValueVO();
                valueVO.setValueId(String.valueOf(spec.getId()));
                valueVO.setValue(spec.getValue());
                valueVO.setPicUrl(spec.getPicUrl());
                values.add(valueVO);
            }
            specVO.setValues(values);
            specVOs.add(specVO);
        }

        int stockTotal = 0;
        int availableTotal = 0;
        Integer minFen = null;
        Integer displayFen = null;
        List<GoodsDetailVO.SkuVO> skuVOs = new ArrayList<>(skus.size());
        for (GoodsProduct sku : skus) {
            int sellable = effectiveSellable(sku, locked);
            int number = sku.getNumber() == null ? 0 : sku.getNumber();
            stockTotal += number;
            availableTotal += sellable;
            Integer fen = GoodsSupport.yuanToFen(sku.getPrice());
            if (fen != null) {
                minFen = minFen == null ? fen : Math.min(minFen, fen);
                if (sellable > 0) {
                    displayFen = displayFen == null ? fen : Math.min(displayFen, fen);
                }
            }

            GoodsDetailVO.SkuVO skuVO = new GoodsDetailVO.SkuVO();
            skuVO.setId(sku.getId());
            skuVO.setSkuCode(sku.getSkuCode());
            skuVO.setSpecValueIds(GoodsSupport.parseSpecValueIds(sku.getSpecValueIds()));
            skuVO.setSpecText(specTextOf(sku, dims, specById));
            skuVO.setPrice(fen);
            skuVO.setNumber(sku.getNumber());
            skuVO.setSellable(lockedUnavailable ? null : sellable);
            skuVO.setUrl(sku.getUrl());
            skuVOs.add(skuVO);
        }
        if (minFen == null) {
            minFen = GoodsSupport.yuanToFen(goods.getRetailPrice() != null
                    ? goods.getRetailPrice() : goods.getCounterPrice());
        }

        GoodsDetailVO detail = new GoodsDetailVO();
        detail.setId(goods.getId());
        detail.setName(goods.getName());
        detail.setBrief(goods.getBrief());
        detail.setPicUrl(goods.getPicUrl());
        detail.setGallery(parseGallery(goods.getGallery()));
        detail.setDetail(goods.getDetail());
        detail.setUnit(goods.getUnit());
        detail.setCounterPrice(GoodsSupport.yuanToFen(goods.getCounterPrice()));
        detail.setCategoryId(goods.getCategoryId());
        detail.setBrandId(goods.getBrandId());
        // R2-10 D-D：详情页要展示「品牌 / 分类」名称（mobile-web GoodsDetail.vue 已写好 brandName/categoryName 渲染）
        detail.setCategoryName(categoryNameOf(goods.getCategoryId()));
        detail.setBrandName(brandNameOf(goods.getBrandId()));
        detail.setPrice(displayFen != null ? displayFen : minFen);
        detail.setStock(stockTotal);
        detail.setAvailableStock(lockedUnavailable ? null : availableTotal);
        detail.setCanBuy(availableTotal > 0);
        detail.setCommentCount(countVisibleComments(goodsId));
        detail.setSpecList(specifications);
        detail.setSpecs(specVOs);
        detail.setSkus(skuVOs);
        detail.setAttributes(toAttributeVOs(attributes));
        detail.setLockedUnavailable(lockedUnavailable);
        return detail;
    }

    /**
     * 分类名（CI-32 详情用，R2-10 D-D）。
     *
     * <p>
     * 单个 ID 逐条查未删除分类即可，但同样守住 {@link #buildAdminRows(List)} 里踩过的两个坑：
     * <b>ID 为 null 直接短路</b>（不拿 null 去查库、更不拿 null 当 Map 键——不可变 Map 会 NPE），
     * <b>不拼 {@code IN ()}</b>（空集合批量查询是 MySQL 语法错误）。
     * 分类不存在或被软删时返回 {@code null}，由前端渲染「—」。
     * </p>
     *
     * @param categoryId 商品类目ID，可空
     * @return 类目名；取不到为 {@code null}
     */
    private String categoryNameOf(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryService.lambdaQuery()
                .eq(Category::getId, categoryId)
                .eq(Category::getDeleted, false)
                .one();
        return category == null ? null : category.getName();
    }

    /**
     * 品牌名（CI-32 详情用，R2-10 D-D）。
     *
     * <p>
     * {@code goods.brand_id} 允许为空（种子数据里就存在无品牌商品），故 null 必须短路；
     * 品牌不存在/被软删同样返回 {@code null}。
     * </p>
     *
     * @param brandId 商品品牌ID，可空
     * @return 品牌名；取不到为 {@code null}
     */
    private String brandNameOf(Long brandId) {
        if (brandId == null) {
            return null;
        }
        Brand brand = brandService.lambdaQuery()
                .eq(Brand::getId, brandId)
                .eq(Brand::getDeleted, false)
                .one();
        return brand == null ? null : brand.getName();
    }

    /**
     * SKU 规格文本：按维度顺序取该 SKU 的取值文本（如「白 / 350ml」）；
     * 存量数据无 {@code spec_value_ids} 时回落 {@code specifications} JSON 数组。
     */
    private String specTextOf(GoodsProduct sku, LinkedHashMap<String, List<GoodsSpecification>> dims,
                              Map<Long, GoodsSpecification> specById) {
        List<String> ids = GoodsSupport.parseSpecValueIds(sku.getSpecValueIds());
        if (!ids.isEmpty()) {
            List<String> texts = new ArrayList<>();
            for (List<GoodsSpecification> values : dims.values()) {
                for (GoodsSpecification spec : values) {
                    if (ids.contains(String.valueOf(spec.getId()))) {
                        texts.add(spec.getValue());
                        break;
                    }
                }
            }
            if (!texts.isEmpty()) {
                return GoodsSupport.buildSpecText(texts);
            }
            // id 与维度对不上（脏数据）：按 id 顺序翻译已知文本
            List<String> fallback = new ArrayList<>();
            for (String id : ids) {
                try {
                    GoodsSpecification spec = specById.get(Long.parseLong(id));
                    if (spec != null) {
                        fallback.add(spec.getValue());
                    }
                } catch (NumberFormatException ignore) {
                    // 非法 id 直接跳过，不抛给 C 端
                }
            }
            return GoodsSupport.buildSpecText(fallback);
        }
        if (sku.getSpecifications() != null && !sku.getSpecifications().isBlank()) {
            try {
                return GoodsSupport.buildSpecText(JSONUtil.toList(sku.getSpecifications(), String.class));
            } catch (Exception ignore) {
                return sku.getSpecifications();
            }
        }
        return "";
    }

    private List<GoodsDetailVO.AttributeVO> toAttributeVOs(List<GoodsAttribute> attributes) {
        List<GoodsDetailVO.AttributeVO> result = new ArrayList<>(attributes.size());
        for (GoodsAttribute attribute : attributes) {
            GoodsDetailVO.AttributeVO vo = new GoodsDetailVO.AttributeVO();
            vo.setName(attribute.getAttribute());
            vo.setValue(attribute.getValue());
            result.add(vo);
        }
        return result;
    }

    private String[] parseGallery(String gallery) {
        if (gallery == null || gallery.isBlank()) {
            return new String[0];
        }
        try {
            return JSONUtil.toList(gallery, String.class).toArray(new String[0]);
        } catch (Exception e) {
            return new String[0];
        }
    }

    /**
     * 未隐藏评价数（BR-CM-04：status=0 才计数）。
     */
    private int countVisibleComments(Long goodsId) {
        long count = commentService.lambdaQuery()
                .eq(Comment::getValueId, goodsId)
                .eq(Comment::getType, 0)
                .eq(Comment::getStatus, 0)
                .eq(Comment::getDeleted, false)
                .count();
        return (int) count;
    }

    /* ==================== 管理端：CI-09 / CI-47 列表 ==================== */

    @Override
    public PageVO<GoodsAdminDTO> adminListGoods(GoodsAdminQuery query) {
        query.normalize();
        List<Integer> statuses = query.getStatuses();
        var wrapper = lambdaQuery()
                .eq(Goods::getDeleted, false)
                .eq(query.getCategoryId() != null, Goods::getCategoryId, query.getCategoryId())
                .eq(query.getBrandId() != null, Goods::getBrandId, query.getBrandId());
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(Goods::getStatus, statuses);
        }
        wrapper.and(query.getKeyword() != null, w -> w
                .like(Goods::getName, query.getKeyword())
                .or().like(Goods::getBrief, query.getKeyword())
                .or().like(Goods::getKeywords, query.getKeyword()));

        boolean memorySort = "displayPrice".equals(query.getSortBy()) || "totalStock".equals(query.getSortBy());
        if (memorySort) {
            List<Goods> all = wrapper.orderByDesc(Goods::getAddTime).list();
            List<GoodsAdminDTO> rows = buildAdminRows(all);
            Comparator<GoodsAdminDTO> comparator = "displayPrice".equals(query.getSortBy())
                    ? Comparator.comparing(GoodsAdminDTO::getDisplayPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                    : Comparator.comparing(GoodsAdminDTO::getTotalStock, Comparator.nullsLast(Comparator.naturalOrder()));
            rows.sort(comparator.thenComparing(GoodsAdminDTO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
            return page(rows, query.getPageNum(), query.getPageSize());
        }
        if ("addTime".equals(query.getSortBy())) {
            wrapper.orderByDesc(Goods::getAddTime);
        } else if ("sortOrder".equals(query.getSortBy())) {
            wrapper.orderByAsc(Goods::getSortOrder);
        } else {
            wrapper.orderByDesc(Goods::getAddTime);
        }
        wrapper.orderByDesc(Goods::getId);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Goods> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        query.getPageNum(), query.getPageSize());
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Goods> result = wrapper.page(page);
        List<Goods> records = result.getRecords();
        if (records == null || records.isEmpty()) {
            return PageVO.empty(result);
        }
        return PageVO.of(result, buildAdminRows(records));
    }

    /**
     * 批量组装管理端列表行（一次取 SKU、分类名、品牌名，避免 N+1）。
     */
    private List<GoodsAdminDTO> buildAdminRows(List<Goods> goodsList) {
        if (goodsList.isEmpty()) {
            return List.of();
        }
        Map<Long, List<GoodsProduct>> productMap = loadProducts(
                goodsList.stream().map(Goods::getId).toList());
        Set<Long> productIds = productMap.values().stream()
                .flatMap(List::stream)
                .map(GoodsProduct::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LockedStockProvider.LockedStockQuery locked = lockedStockProvider.query(productIds);

        Set<Long> categoryIds = goodsList.stream().map(Goods::getCategoryId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> brandIds = goodsList.stream().map(Goods::getBrandId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        // 空集合必须短路：listByIds(empty) 会拼出 `IN ()`，MySQL 直接语法错误；
        // 另外 goods.brandId 可为 null，Map.of() 这类不可变 Map 用 null 键取值会抛 NPE，故用 HashMap。
        Map<Long, String> categoryNames = categoryIds.isEmpty()
                ? new HashMap<>()
                : categoryService.listByIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a, HashMap::new));
        Map<Long, String> brandNames = brandIds.isEmpty()
                ? new HashMap<>()
                : brandService.listByIds(brandIds).stream()
                        .collect(Collectors.toMap(Brand::getId, Brand::getName, (a, b) -> a, HashMap::new));

        List<GoodsAdminDTO> rows = new ArrayList<>(goodsList.size());
        for (Goods goods : goodsList) {
            List<GoodsProduct> skus = productMap.getOrDefault(goods.getId(), List.of());
            GoodsVO summary = buildListRow(goods, skus, locked);

            GoodsAdminDTO dto = new GoodsAdminDTO();
            dto.setId(goods.getId());
            dto.setName(goods.getName());
            dto.setBrief(goods.getBrief());
            dto.setPicUrl(goods.getPicUrl());
            dto.setDetail(goods.getDetail());
            dto.setUnit(goods.getUnit());
            dto.setKeywords(goods.getKeywords());
            dto.setSortOrder(goods.getSortOrder());
            dto.setCategoryId(goods.getCategoryId());
            dto.setCategoryName(categoryNames.get(goods.getCategoryId()));
            dto.setBrandId(goods.getBrandId());
            dto.setBrandName(brandNames.get(goods.getBrandId()));
            dto.setMinPrice(summary.getMinPrice());
            dto.setDisplayPrice(summary.getDisplayPrice());
            dto.setTotalStock(summary.getTotalStock());
            dto.setSkuCount(skus.size());
            dto.setOnSale(Boolean.TRUE.equals(goods.getIsOnSale()));
            dto.setStatus(goods.getStatus());
            dto.setStatusText(GoodsSupport.statusText(goods.getStatus()));
            dto.setCounterPrice(GoodsSupport.yuanToFen(goods.getCounterPrice()));
            dto.setGallery(parseGalleryList(goods.getGallery()));
            dto.setAddTime(GoodsSupport.formatTime(goods.getAddTime()));
            dto.setUpdateTime(GoodsSupport.formatTime(goods.getUpdateTime()));
            // 编辑页需要规格矩阵：列表行一并带回（避免 admin 再发一次详情请求）
            dto.setSpecifications(buildSpecDTOs(goods.getId()));
            dto.setSkus(buildSkuDTOs(skus));
            rows.add(dto);
        }
        return rows;
    }

    private List<String> parseGalleryList(String gallery) {
        if (gallery == null || gallery.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JSONUtil.toList(gallery, String.class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<GoodsAdminDTO.SpecDTO> buildSpecDTOs(Long goodsId) {
        List<GoodsSpecification> specs = specificationService.lambdaQuery()
                .eq(GoodsSpecification::getGoodsId, goodsId)
                .eq(GoodsSpecification::getDeleted, false)
                .orderByAsc(GoodsSpecification::getId)
                .list();
        Map<String, List<String>> dims = new LinkedHashMap<>();
        for (GoodsSpecification spec : specs) {
            dims.computeIfAbsent(spec.getSpecification(), k -> new ArrayList<>()).add(spec.getValue());
        }
        List<GoodsAdminDTO.SpecDTO> result = new ArrayList<>(dims.size());
        for (Map.Entry<String, List<String>> entry : dims.entrySet()) {
            GoodsAdminDTO.SpecDTO dto = new GoodsAdminDTO.SpecDTO();
            dto.setName(entry.getKey());
            dto.setValues(entry.getValue());
            result.add(dto);
        }
        return result;
    }

    private List<GoodsAdminDTO.SkuDTO> buildSkuDTOs(List<GoodsProduct> skus) {
        List<GoodsAdminDTO.SkuDTO> result = new ArrayList<>(skus.size());
        for (GoodsProduct sku : skus) {
            GoodsAdminDTO.SkuDTO dto = new GoodsAdminDTO.SkuDTO();
            dto.setId(sku.getId());
            dto.setSpecValueIds(GoodsSupport.parseSpecValueIds(sku.getSpecValueIds()));
            dto.setPrice(GoodsSupport.yuanToFen(sku.getPrice()));
            dto.setNumber(sku.getNumber());
            dto.setUrl(sku.getUrl());
            dto.setSkuCode(sku.getSkuCode());
            result.add(dto);
        }
        return result;
    }

    @Override
    public long adminCountGoods() {
        return lambdaQuery().eq(Goods::getDeleted, false).count();
    }

    /* ==================== 通用工具 ==================== */

    /**
     * 按商品ID批量取 SKU（分片，避开 IN 长度限制）。
     */
    private Map<Long, List<GoodsProduct>> loadProducts(Collection<Long> goodsIds) {
        Map<Long, List<GoodsProduct>> result = new HashMap<>();
        List<Long> ids = new ArrayList<>(goodsIds);
        for (int i = 0; i < ids.size(); i += IN_CHUNK) {
            List<Long> chunk = ids.subList(i, Math.min(i + IN_CHUNK, ids.size()));
            List<GoodsProduct> products = goodsProductService.lambdaQuery()
                    .in(GoodsProduct::getGoodsId, chunk)
                    .eq(GoodsProduct::getDeleted, false)
                    .list();
            for (GoodsProduct product : products) {
                result.computeIfAbsent(product.getGoodsId(), k -> new ArrayList<>()).add(product);
            }
        }
        return result;
    }

    /**
     * 内存分页（价格/可售过滤与排序必须在汇总 sellable 之后做，无法交给 SQL）。
     */
    private <T> PageVO<T> page(List<T> rows, int pageNum, int pageSize) {
        int total = rows.size();
        long pages = total == 0 ? 0L : (total + (long) pageSize - 1) / pageSize;
        long from = (long) (pageNum - 1) * pageSize;
        if (from >= total) {
            return new PageVO<>((long) total, pages, new ArrayList<>());
        }
        int to = (int) Math.min(from + pageSize, total);
        return new PageVO<>((long) total, pages, new ArrayList<>(rows.subList((int) from, to)));
    }
}
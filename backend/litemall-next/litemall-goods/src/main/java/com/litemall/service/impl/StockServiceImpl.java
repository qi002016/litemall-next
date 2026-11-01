package com.litemall.service.impl;

import com.litemall.domain.DTO.StockAdminQuery;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.PO.GoodsSpecification;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.DeductBatchResultDTO;
import com.litemall.domain.dto.ReleasedBatchResultDTO;
import com.litemall.domain.dto.StockAdminDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.GoodsApiException;
import com.litemall.exception.InsufficientStockException;
import com.litemall.service.IGoodsProductService;
import com.litemall.service.IGoodsService;
import com.litemall.service.IGoodsSpecificationService;
import com.litemall.service.IStockService;
import com.litemall.support.GoodsSupport;
import com.litemall.support.LockedStockProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 库存服务实现（08 v4 CN-13 / CN-14 / CI-42 / CI-43 / CI-44 / CI-45）。
 * </p>
 *
 * <p>
 * 库存写操作全部走<b>条件更新</b>（{@code WHERE number >= n} 或 {@code WHERE number = expect}），
 * 靠 affected rows 判定是否成功，不做「先查后写」——后者在并发下会把库存写穿。
 * </p>
 *
 * <p>
 * locked 是 order 域派生的只读量：管理端展示时要显示它，但调整只校验「不能低于 locked」，
 * 校验失败返回 400 STOCK_ADJUST_CONFLICT，而不是让 number 掉到 locked 以下。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements IStockService {

    private final IGoodsProductService goodsProductService;
    private final IGoodsService goodsService;
    private final IGoodsSpecificationService specificationService;
    private final LockedStockProvider lockedStockProvider;

    /* ==================== CN-13 库存列表 ==================== */

    @Override
    public PageVO<StockAdminDTO> adminStockList(StockAdminQuery query) {
        List<Integer> statuses = query.parseStatuses();
        String keyword = query.keywordOrNull();
        if (keyword != null && keyword.length() > 50) {
            throw GoodsApiException.paramInvalid("搜索关键字最多 50 字");
        }

        // 1. 先按商品维度筛选（类目/状态/名称关键字）
        List<Goods> goodsList = goodsService.lambdaQuery()
                .eq(Goods::getDeleted, false)
                .eq(query.getCategoryId() != null, Goods::getCategoryId, query.getCategoryId())
                .in(statuses != null, Goods::getStatus, statuses == null ? List.of() : statuses)
                .list();
        Map<Long, Goods> goodsMap = goodsList.stream()
                .collect(Collectors.toMap(Goods::getId, g -> g, (a, b) -> a));

        // 2. 货品维度筛选：SKU 码命中 或 所属商品名命中
        Set<Long> goodsIdByName = null;
        if (keyword != null) {
            goodsIdByName = goodsList.stream()
                    .filter(g -> containsIgnoreCase(g.getName(), keyword))
                    .map(Goods::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        var productQuery = goodsProductService.lambdaQuery().eq(GoodsProduct::getDeleted, false);
        if (query.getCategoryId() != null || statuses != null) {
            if (goodsMap.isEmpty()) {
                return PageVO.empty(0L, 0L);
            }
            productQuery.in(GoodsProduct::getGoodsId, goodsMap.keySet());
        }
        if (keyword != null) {
            Set<Long> nameHits = goodsIdByName;
            productQuery.and(w -> {
                w.like(GoodsProduct::getSkuCode, keyword);
                if (nameHits != null && !nameHits.isEmpty()) {
                    w.or().in(GoodsProduct::getGoodsId, nameHits);
                }
            });
        }
        List<GoodsProduct> products = productQuery.list();
        if (products.isEmpty()) {
            return PageVO.empty(0L, 0L);
        }

        // 3. 一次性取 locked（order CN-IN-04，失败则整页 lockedUnavailable）
        Set<Long> productIds = products.stream().map(GoodsProduct::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LockedStockProvider.LockedStockQuery locked = lockedStockProvider.query(productIds);
        boolean lockedUnavailable = !locked.available();

        Map<Long, Goods> goodsForRows = loadGoodsFor(products, goodsMap);
        Map<Long, String> valueTexts = loadSpecValueTexts(products);

        List<StockAdminDTO> rows = new ArrayList<>(products.size());
        for (GoodsProduct product : products) {
            Goods goods = goodsForRows.get(product.getGoodsId());
            StockAdminDTO row = buildRow(product, goods, locked, valueTexts);
            if (Boolean.TRUE.equals(query.getLowStockOnly()) && !Boolean.TRUE.equals(row.getLowStock())) {
                continue;
            }
            if (Boolean.TRUE.equals(query.getSoldOutOnly())
                    && (row.getSellable() == null || row.getSellable() > 0)) {
                continue;
            }
            rows.add(row);
        }

        sortStockRows(rows, query.sortByOrDefault());
        return page(rows, query.pageNumOrDefault(), query.pageSizeOrDefault());
    }

    private StockAdminDTO buildRow(GoodsProduct product, Goods goods,
                                   LockedStockProvider.LockedStockQuery locked,
                                   Map<Long, String> valueTexts) {
        StockAdminDTO row = new StockAdminDTO();
        row.setProductId(product.getId());
        row.setSkuCode(product.getSkuCode());
        row.setGoodsId(product.getGoodsId());
        if (goods != null) {
            row.setGoodsName(goods.getName());
            row.setGoodsStatus(goods.getStatus());
            row.setGoodsStatusText(GoodsSupport.statusText(goods.getStatus()));
            row.setPicUrl(goods.getPicUrl());
        }
        row.setSpecText(specTextOf(product, valueTexts));
        row.setNumber(product.getNumber());
        Integer lockedQty = locked.lockedOf(product.getId());
        row.setLocked(lockedQty);
        Integer sellable = GoodsSupport.sellable(product.getNumber(), lockedQty, !locked.available());
        row.setSellable(sellable);
        row.setLowStock(GoodsSupport.lowStock(sellable));
        row.setLowStockThreshold(GoodsSupport.LOW_STOCK_THRESHOLD);
        row.setLockedUnavailable(!locked.available());
        row.setUpdateTime(GoodsSupport.formatTime(product.getUpdateTime()));
        return row;
    }

    private void sortStockRows(List<StockAdminDTO> rows, String sortBy) {
        switch (sortBy) {
            case "number" -> rows.sort(Comparator.comparing(StockAdminDTO::getNumber,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            case "updateTime" -> rows.sort(Comparator.comparing(StockAdminDTO::getUpdateTime,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            default -> rows.sort(Comparator.comparing(StockAdminDTO::getSellable,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }
    }

    /**
     * 规格文本：按 spec_value_ids 的 id 升序翻译成取值文本（建品时按维度顺序写入，故顺序即维度顺序）。
     */
    private String specTextOf(GoodsProduct product, Map<Long, String> valueTexts) {
        List<String> ids = GoodsSupport.parseSpecValueIds(product.getSpecValueIds());
        if (ids.isEmpty()) {
            return "";
        }
        List<String> texts = new ArrayList<>(ids.size());
        for (String id : ids) {
            try {
                String text = valueTexts.get(Long.parseLong(id));
                if (text != null) {
                    texts.add(text);
                }
            } catch (NumberFormatException ignore) {
                // 脏数据（非数字）跳过，不影响库存展示
            }
        }
        return GoodsSupport.buildSpecText(texts);
    }

    private Map<Long, Goods> loadGoodsFor(List<GoodsProduct> products, Map<Long, Goods> known) {
        Set<Long> missing = products.stream()
                .map(GoodsProduct::getGoodsId)
                .filter(id -> id != null && !known.containsKey(id))
                .collect(Collectors.toSet());
        if (missing.isEmpty()) {
            return known;
        }
        Map<Long, Goods> result = new HashMap<>(known);
        for (Goods goods : goodsService.listByIds(missing)) {
            result.put(goods.getId(), goods);
        }
        return result;
    }

    private Map<Long, String> loadSpecValueTexts(List<GoodsProduct> products) {
        Set<Long> goodsIds = products.stream().map(GoodsProduct::getGoodsId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        List<GoodsSpecification> specs = specificationService.lambdaQuery()
                .in(GoodsSpecification::getGoodsId, goodsIds)
                .eq(GoodsSpecification::getDeleted, false)
                .list();
        Map<Long, String> texts = new HashMap<>(specs.size());
        for (GoodsSpecification spec : specs) {
            texts.put(spec.getId(), spec.getValue());
        }
        return texts;
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && source.toLowerCase(java.util.Locale.ROOT)
                .contains(keyword.toLowerCase(java.util.Locale.ROOT));
    }

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

    /* ==================== CN-14 库存调整 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockAdminDTO adminStockUpdate(StockAdminDTO dto) {
        if (dto == null || dto.getProductId() == null) {
            throw GoodsApiException.paramInvalid("货品ID不能为空");
        }
        if (dto.getNumber() == null || dto.getNumber() < 0) {
            throw GoodsApiException.paramInvalid("库存数量必须大于等于 0");
        }
        if (dto.getRemark() != null && dto.getRemark().length() > 100) {
            throw GoodsApiException.paramInvalid("备注最长 100 字");
        }
        GoodsProduct product = goodsProductService.getById(dto.getProductId());
        if (product == null || Boolean.TRUE.equals(product.getDeleted())) {
            throw GoodsApiException.notFound("货品不存在");
        }

        LockedStockProvider.LockedStockQuery locked = lockedStockProvider.query(List.of(product.getId()));
        Integer lockedQty = locked.lockedOf(product.getId());
        if (lockedQty != null && dto.getNumber() < lockedQty) {
            throw GoodsApiException.stockAdjustConflict(lockedQty);
        }

        LocalDateTime now = LocalDateTime.now();
        // CAS：带 expectNumber 时以编辑页读到的库存为乐观锁；未带时以本次读到的库存为锁
        Integer expect = dto.getExpectNumber() != null ? dto.getExpectNumber() : product.getNumber();
        boolean ok = goodsProductService.lambdaUpdate()
                .eq(GoodsProduct::getId, product.getId())
                .eq(GoodsProduct::getDeleted, false)
                .eq(GoodsProduct::getNumber, expect)
                .set(GoodsProduct::getNumber, dto.getNumber())
                .set(GoodsProduct::getUpdateTime, now)
                .update();
        if (!ok) {
            throw GoodsApiException.concurrentModified();
        }

        StockAdminDTO result = new StockAdminDTO();
        result.setProductId(product.getId());
        result.setNumber(dto.getNumber());
        result.setLocked(lockedQty);
        Integer sellable = GoodsSupport.sellable(dto.getNumber(), lockedQty, !locked.available());
        result.setSellable(sellable);
        result.setLowStock(GoodsSupport.lowStock(sellable));
        result.setLowStockThreshold(GoodsSupport.LOW_STOCK_THRESHOLD);
        result.setLockedUnavailable(!locked.available());
        result.setUpdateTime(GoodsSupport.formatTime(now));
        return result;
    }

    /* ==================== CI-42 / CI-43 单条扣减与回补 ==================== */

    @Override
    public void deduct(Long productId, Integer number) {
        validateChange(productId, number, "扣库存参数错误");
        boolean ok = goodsProductService.lambdaUpdate()
                .eq(GoodsProduct::getId, productId)
                .eq(GoodsProduct::getDeleted, false)
                .ge(GoodsProduct::getNumber, number)
                .setSql("number = number - {0}", number)
                .set(GoodsProduct::getUpdateTime, LocalDateTime.now())
                .update();
        if (!ok) {
            throw GoodsApiException.paramInvalid("库存不足");
        }
    }

    @Override
    public void release(Long productId, Integer number) {
        validateChange(productId, number, "回补库存参数错误");
        goodsProductService.lambdaUpdate()
                .eq(GoodsProduct::getId, productId)
                .eq(GoodsProduct::getDeleted, false)
                .setSql("number = number + {0}", number)
                .set(GoodsProduct::getUpdateTime, LocalDateTime.now())
                .update();
    }

    /* ==================== CI-44 / CI-45 批量 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeductBatchResultDTO deductBatch(StockChangeDTO dto) {
        List<StockChangeDTO.Item> items = dto == null ? null : dto.getItems();
        if (items == null || items.isEmpty()) {
            throw GoodsApiException.paramInvalid("扣库存参数错误");
        }
        List<StockChangeDTO.Item> ordered = new ArrayList<>(items);
        // 固定加锁顺序（productId 升序），避免并发下单互相等待造成死锁
        ordered.sort(Comparator.comparing(StockChangeDTO.Item::getProductId,
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (StockChangeDTO.Item item : ordered) {
            validateChange(item == null ? null : item.getProductId(),
                    item == null ? null : item.getNumber(), "扣库存参数错误");
        }

        Map<Long, DeductBatchResultDTO.Item> byProductId = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (StockChangeDTO.Item item : ordered) {
            boolean ok = goodsProductService.lambdaUpdate()
                    .eq(GoodsProduct::getId, item.getProductId())
                    .eq(GoodsProduct::getDeleted, false)
                    .ge(GoodsProduct::getNumber, item.getNumber())
                    .setSql("number = number - {0}", item.getNumber())
                    .set(GoodsProduct::getUpdateTime, now)
                    .update();
            DeductBatchResultDTO.Item result = new DeductBatchResultDTO.Item();
            result.setProductId(item.getProductId());
            result.setDeducted(ok);
            byProductId.put(item.getProductId(), result);
            if (!ok) {
                GoodsProduct current = goodsProductService.getById(item.getProductId());
                int available = current == null || current.getNumber() == null ? 0 : current.getNumber();
                result.setShortage(Math.max(0, item.getNumber() - available));
                // 整批回滚：所有项（含本轮已扣成功的）一律如实标记为未扣减
                for (DeductBatchResultDTO.Item each : byProductId.values()) {
                    each.setDeducted(false);
                }
                // 抛给 controller，由事务边界整批回滚（不做部分扣减）
                throw new InsufficientStockException(inRequestOrder(items, byProductId));
            }
        }
        return DeductBatchResultDTO.success(inRequestOrder(items, byProductId));
    }

    /**
     * 按请求 items 的原始顺序重排结果（契约：{@code items} 顺序与请求一致），
     * 与「加锁顺序按 productId 升序」解耦。
     */
    private List<DeductBatchResultDTO.Item> inRequestOrder(List<StockChangeDTO.Item> items,
                                                          Map<Long, DeductBatchResultDTO.Item> byProductId) {
        List<DeductBatchResultDTO.Item> ordered = new ArrayList<>(items.size());
        for (StockChangeDTO.Item item : items) {
            DeductBatchResultDTO.Item result = byProductId.get(item.getProductId());
            if (result != null) {
                ordered.add(result);
            }
        }
        return ordered;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleasedBatchResultDTO releaseBatch(StockChangeDTO dto) {
        List<StockChangeDTO.Item> items = dto == null ? null : dto.getItems();
        if (items == null || items.isEmpty()) {
            throw GoodsApiException.paramInvalid("回补库存参数错误");
        }
        List<ReleasedBatchResultDTO.Item> results = new ArrayList<>(items.size());
        LocalDateTime now = LocalDateTime.now();
        for (StockChangeDTO.Item item : items) {
            if (item == null || item.getProductId() == null
                    || item.getNumber() == null || item.getNumber() <= 0) {
                throw GoodsApiException.paramInvalid("回补库存参数错误");
            }
            boolean ok = goodsProductService.lambdaUpdate()
                    .eq(GoodsProduct::getId, item.getProductId())
                    .eq(GoodsProduct::getDeleted, false)
                    .setSql("number = number + {0}", item.getNumber())
                    .set(GoodsProduct::getUpdateTime, now)
                    .update();
            ReleasedBatchResultDTO.Item result = new ReleasedBatchResultDTO.Item();
            result.setProductId(item.getProductId());
            result.setReleased(ok);
            results.add(result);
        }
        return ReleasedBatchResultDTO.of(results);
    }

    private void validateChange(Long productId, Integer number, String message) {
        if (productId == null || number == null || number <= 0) {
            throw GoodsApiException.paramInvalid(message);
        }
    }
}
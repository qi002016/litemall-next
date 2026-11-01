package com.litemall.service.impl;

import cn.hutool.json.JSONUtil;
import com.litemall.domain.PO.Category;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.PO.GoodsSpecification;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.exception.GoodsApiException;
import com.litemall.mapper.OrderGoodsRefMapper;
import com.litemall.service.ICategoryService;
import com.litemall.service.IGoodsProductService;
import com.litemall.service.IGoodsService;
import com.litemall.service.IGoodsSpecificationService;
import com.litemall.service.IGoodsWriteService;
import com.litemall.support.GoodsSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <p>
 * 商品写路径实现（08 v4 CI-10 / CI-11 / CI-12）。
 * </p>
 *
 * <p>
 * 三条不可动摇的落库约束（BR-GOODS-02 / BR-GOODS-07，队长口径）：
 * </p>
 * <ol>
 *   <li>每个 SKU <b>同时</b>写 {@code spec_value_ids}（升序逗号串）与 {@code sku_code}；</li>
 *   <li>{@code sku_code} 缺省由服务端生成（{@code goodsId-<值ID升序用_连接>}，无规格为 {@code goodsId-D}），生成后不随规格改名而变；</li>
 *   <li>金额在服务边界完成「分 ↔ 元」换算，库内仍是 DECIMAL 元。</li>
 * </ol>
 *
 * <p>
 * 状态口径：{@code status}(0 草稿 / 1 在售 / 2 已下架) 是唯一真相，{@code is_on_sale} 是它的
 * 布尔投影（仅 status=1 ⇒ true），两者同时出现在入参且矛盾时直接 400。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Service
@RequiredArgsConstructor
public class GoodsWriteServiceImpl implements IGoodsWriteService {

    /** 缺省规格维度名（未传 specifications 时） */
    private static final String DEFAULT_SPEC_NAME = "规格";
    /** 缺省规格值（未传 specifications 时） */
    private static final String DEFAULT_SPEC_VALUE = "默认";
    /** 单商户期固定商户（BR-ADM-06） */
    private static final long DEFAULT_MERCHANT_ID = 1L;
    /** 排序权重缺省值 */
    private static final int DEFAULT_SORT_ORDER = 100;
    /** 缺省单位 */
    private static final String DEFAULT_UNIT = "件";
    /** 图集上限 */
    private static final int MAX_GALLERY = 8;
    /** 规格维度上限 */
    private static final int MAX_SPEC_DIMS = 2;
    /** SKU 码长度上限 */
    private static final int MAX_SKU_CODE_LEN = 64;

    private final IGoodsService goodsService;
    private final IGoodsProductService goodsProductService;
    private final IGoodsSpecificationService specificationService;
    private final ICategoryService categoryService;
    private final OrderGoodsRefMapper orderGoodsRefMapper;

    /* ==================== CI-10 新建 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long adminCreateGoods(GoodsAdminDTO dto) {
        validateBase(dto, true);
        int status = resolveStatus(dto.getStatus(), dto.getOnSale(), null);

        Goods goods = new Goods();
        applyBaseFields(goods, dto);
        goods.setStatus(status);
        goods.setIsOnSale(status == 1);
        goods.setMerchantId(DEFAULT_MERCHANT_ID);
        goods.setDeleted(false);
        LocalDateTime now = LocalDateTime.now();
        goods.setAddTime(now);
        goods.setUpdateTime(now);
        if (dto.getSkus() == null || dto.getSkus().isEmpty()) {
            // 无 SKU 入参：零售价直接取 minPrice（分 → 元），保证列表不出现空价
            goods.setRetailPrice(GoodsSupport.fenToYuan(dto.getMinPrice()));
        }
        goodsService.save(goods);
        Long goodsId = goods.getId();

        List<GoodsSpecification> specs = rebuildSpecifications(goodsId,
                normalizeSpecs(dto.getSpecifications()));
        List<GoodsAdminDTO.SkuDTO> skus = dto.getSkus();
        if (skus == null || skus.isEmpty()) {
            insertDefaultSku(goodsId, dto, specs);
        } else {
            // 与编辑共用同一套校验：规格组合去重、skuCode 唯一、金额/库存合法
            overwriteSkus(goodsId, skus, specs);
            syncRetailPrice(goodsId);
        }
        return goodsId;
    }

    /**
     * 缺省 SKU：仅在「1 个维度、1 个取值」时成立，否则说明规格矩阵与 SKU 不匹配。
     */
    private void insertDefaultSku(Long goodsId, GoodsAdminDTO dto, List<GoodsSpecification> specs) {
        if (specs.size() != 1) {
            throw GoodsApiException.paramInvalid("缺少 SKU 数据：多个规格取值必须提交完整 SKU 矩阵");
        }
        if (dto.getMinPrice() == null) {
            throw GoodsApiException.paramInvalid("售价不能为空");
        }
        BigDecimal price = GoodsSupport.fenToYuan(dto.getMinPrice());
        if (price == null || price.signum() <= 0) {
            throw GoodsApiException.paramInvalid("售价必须大于 0");
        }
        GoodsProduct product = new GoodsProduct();
        product.setGoodsId(goodsId);
        product.setSpecifications(JSONUtil.toJsonStr(List.of(specs.get(0).getValue())));
        product.setSpecValueIds(String.valueOf(specs.get(0).getId()));
        // 默认 SKU 的业务码固定为 <goodsId>-D（不随规格值ID变化，便于对外稳定引用）
        product.setSkuCode(goodsId + "-" + GoodsSupport.NO_SPEC_CODE);
        product.setPrice(price);
        product.setNumber(0);
        product.setUrl(dto.getPicUrl());
        LocalDateTime now = LocalDateTime.now();
        product.setAddTime(now);
        product.setUpdateTime(now);
        product.setDeleted(false);
        goodsProductService.save(product);
    }

    /* ==================== CI-11 编辑 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdateGoods(GoodsAdminDTO dto) {
        if (dto.getId() == null) {
            throw GoodsApiException.paramInvalid("商品ID不能为空");
        }
        Goods existing = goodsService.getById(dto.getId());
        if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
            throw GoodsApiException.notFound("商品不存在");
        }
        validateBase(dto, false);
        int status = resolveStatus(dto.getStatus(), dto.getOnSale(), existing.getStatus());

        LocalDateTime expectUpdateTime = null;
        if (dto.getUpdateTime() != null) {
            expectUpdateTime = GoodsSupport.parseTime(dto.getUpdateTime());
            if (expectUpdateTime == null) {
                throw GoodsApiException.paramInvalid("updateTime 格式应为 yyyy-MM-dd HH:mm:ss");
            }
        }

        Goods update = new Goods();
        update.setId(dto.getId());
        applyBaseFields(update, dto);
        update.setStatus(status);
        update.setIsOnSale(status == 1);

        // 规格维度：传入即全量重建；重建会让已有 SKU 的 spec_value_ids 失效，故必须同时提交 SKU
        List<GoodsSpecification> specs = null;
        if (dto.getSpecifications() != null) {
            if (dto.getSkus() == null || dto.getSkus().isEmpty()) {
                throw GoodsApiException.paramInvalid("修改规格维度时必须同时提交 SKU 数据");
            }
            specs = rebuildSpecifications(dto.getId(), normalizeSpecs(dto.getSpecifications()));
        }
        if (dto.getSkus() != null) {
            if (dto.getSkus().isEmpty()) {
                throw GoodsApiException.paramInvalid("至少保留一个 SKU");
            }
            if (specs == null) {
                specs = currentSpecifications(dto.getId());
            }
            overwriteSkus(dto.getId(), dto.getSkus(), specs);
            syncRetailPrice(dto.getId());
        }

        update.setUpdateTime(LocalDateTime.now());
        var wrapper = goodsService.lambdaUpdate()
                .eq(Goods::getId, dto.getId())
                .eq(Goods::getDeleted, false);
        if (expectUpdateTime != null) {
            // 并发 CAS：以编辑页读到的 update_time 作为乐观锁
            wrapper.eq(Goods::getUpdateTime, expectUpdateTime);
        }
        boolean ok = wrapper.update(update);
        if (!ok) {
            throw expectUpdateTime != null
                    ? GoodsApiException.concurrentModified()
                    : GoodsApiException.notFound("商品不存在");
        }
    }

    /**
     * SKU 全量覆盖：带 id 更新、不带 id 新建、未出现且被订单引用 ⇒ 409、未出现且无引用 ⇒ 逻辑删除。
     */
    private void overwriteSkus(Long goodsId, List<GoodsAdminDTO.SkuDTO> skus, List<GoodsSpecification> specs) {
        List<GoodsProduct> existing = goodsProductService.lambdaQuery()
                .eq(GoodsProduct::getGoodsId, goodsId)
                .eq(GoodsProduct::getDeleted, false)
                .list();
        Map<Long, GoodsProduct> existingMap = new HashMap<>();
        for (GoodsProduct product : existing) {
            existingMap.put(product.getId(), product);
        }

        Set<String> comboSeen = new HashSet<>();
        Set<String> codeSeen = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (GoodsAdminDTO.SkuDTO sku : skus) {
            List<Long> valueIds = resolveSpecValueIds(sku.getSpecValueIds(), specs);
            String comboKey = joinIds(valueIds);
            if (!comboSeen.add(comboKey)) {
                throw GoodsApiException.paramInvalid("规格组合重复");
            }
            if (sku.getPrice() == null || sku.getPrice() < 1) {
                throw GoodsApiException.paramInvalid("售价必须大于 0");
            }
            if (sku.getNumber() != null && sku.getNumber() < 0) {
                throw GoodsApiException.paramInvalid("库存不能为负");
            }
            String skuCode = resolveSkuCode(goodsId, sku, valueIds);
            if (!codeSeen.add(skuCode)) {
                throw GoodsApiException.paramInvalid("SKU 编码重复：" + skuCode);
            }
            assertSkuCodeUnique(skuCode, sku.getId());

            GoodsProduct product;
            if (sku.getId() != null) {
                product = existingMap.remove(sku.getId());
                if (product == null) {
                    throw GoodsApiException.paramInvalid("SKU 不属于该商品：" + sku.getId());
                }
            } else {
                product = new GoodsProduct();
                product.setGoodsId(goodsId);
                product.setAddTime(now);
                product.setDeleted(false);
            }
            product.setSpecValueIds(comboKey);
            product.setSpecifications(JSONUtil.toJsonStr(specTexts(valueIds, specs)));
            product.setSkuCode(skuCode);
            product.setPrice(GoodsSupport.fenToYuan(sku.getPrice()));
            product.setNumber(sku.getNumber() == null ? 0 : sku.getNumber());
            product.setUrl(sku.getUrl());
            product.setUpdateTime(now);
            if (product.getId() == null) {
                goodsProductService.save(product);
            } else {
                goodsProductService.updateById(product);
            }
        }

        // 未出现的存量 SKU：被订单引用只能置库存 0，绝不允许删（否则历史订单失去商品行）
        for (GoodsProduct orphan : existingMap.values()) {
            if (orderGoodsRefMapper.countByProductId(orphan.getId()) > 0) {
                throw GoodsApiException.skuInUse(
                        "该规格已被订单引用，只能把库存置 0，不能删除");
            }
            GoodsProduct delete = new GoodsProduct();
            delete.setId(orphan.getId());
            delete.setDeleted(true);
            delete.setUpdateTime(now);
            goodsProductService.updateById(delete);
        }
    }

    /* ==================== CI-12 删除 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteGoods(Long id) {
        if (id == null) {
            throw GoodsApiException.paramInvalid("商品ID不能为空");
        }
        Goods goods = goodsService.getById(id);
        if (goods == null || Boolean.TRUE.equals(goods.getDeleted())) {
            throw GoodsApiException.notFound("商品不存在");
        }
        if (!Integer.valueOf(0).equals(goods.getStatus())) {
            throw GoodsApiException.goodsInUse();
        }
        if (orderGoodsRefMapper.countByGoodsId(id) > 0) {
            throw GoodsApiException.goodsInUse();
        }
        boolean ok = goodsService.lambdaUpdate()
                .eq(Goods::getId, id)
                .eq(Goods::getDeleted, false)
                .eq(Goods::getStatus, 0)
                .set(Goods::getDeleted, true)
                .set(Goods::getUpdateTime, LocalDateTime.now())
                .update();
        if (!ok) {
            // CAS 失败：并发下已被删除或已上架
            throw GoodsApiException.notFound("商品不存在");
        }
    }

    /* ==================== 规格维度 ==================== */

    /** 归一规格：缺省 ⇒ 单维度「规格/默认」 */
    private List<GoodsAdminDTO.SpecDTO> normalizeSpecs(List<GoodsAdminDTO.SpecDTO> specs) {
        if (specs == null || specs.isEmpty()) {
            GoodsAdminDTO.SpecDTO def = new GoodsAdminDTO.SpecDTO();
            def.setName(DEFAULT_SPEC_NAME);
            def.setValues(new ArrayList<>(List.of(DEFAULT_SPEC_VALUE)));
            return new ArrayList<>(List.of(def));
        }
        if (specs.size() > MAX_SPEC_DIMS) {
            throw GoodsApiException.paramInvalid("规格维度最多 " + MAX_SPEC_DIMS + " 个");
        }
        Set<String> names = new HashSet<>();
        for (GoodsAdminDTO.SpecDTO spec : specs) {
            String name = spec.getName() == null ? "" : spec.getName().trim();
            if (name.isEmpty() || name.length() > 32) {
                throw GoodsApiException.paramInvalid("规格名称必须为 1–32 字");
            }
            if (!names.add(name)) {
                throw GoodsApiException.paramInvalid("规格名称重复：" + name);
            }
            if (spec.getValues() == null || spec.getValues().isEmpty()) {
                throw GoodsApiException.paramInvalid("规格「" + name + "」至少需要一个取值");
            }
            Set<String> values = new HashSet<>();
            for (String value : spec.getValues()) {
                String text = value == null ? "" : value.trim();
                if (text.isEmpty()) {
                    throw GoodsApiException.paramInvalid("规格「" + name + "」的取值不能为空");
                }
                if (!values.add(text)) {
                    throw GoodsApiException.paramInvalid("规格「" + name + "」取值重复：" + text);
                }
            }
        }
        return specs;
    }

    /** 全量重建规格行：旧的逻辑删除，新的按维度顺序插入 */
    private List<GoodsSpecification> rebuildSpecifications(Long goodsId, List<GoodsAdminDTO.SpecDTO> specs) {
        LocalDateTime now = LocalDateTime.now();
        specificationService.lambdaUpdate()
                .eq(GoodsSpecification::getGoodsId, goodsId)
                .eq(GoodsSpecification::getDeleted, false)
                .set(GoodsSpecification::getDeleted, true)
                .set(GoodsSpecification::getUpdateTime, now)
                .update();
        List<GoodsSpecification> created = new ArrayList<>();
        for (GoodsAdminDTO.SpecDTO spec : specs) {
            for (String value : spec.getValues()) {
                GoodsSpecification row = new GoodsSpecification();
                row.setGoodsId(goodsId);
                row.setSpecification(spec.getName().trim());
                row.setValue(value == null ? null : value.trim());
                row.setDeleted(false);
                row.setAddTime(now);
                row.setUpdateTime(now);
                specificationService.save(row);
                created.add(row);
            }
        }
        return created;
    }

    private List<GoodsSpecification> currentSpecifications(Long goodsId) {
        return specificationService.lambdaQuery()
                .eq(GoodsSpecification::getGoodsId, goodsId)
                .eq(GoodsSpecification::getDeleted, false)
                .orderByAsc(GoodsSpecification::getId)
                .list();
    }

    /**
     * 规格值解析：新建时前端可能传临时序号（1 起的序号）或取值文本，编辑时必须传真 ID。
     * 三种写法都兼容，解析失败一律 400。
     */
    private List<Long> resolveSpecValueIds(List<String> raw, List<GoodsSpecification> specs) {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return ids;
        }
        Map<String, Long> byId = new HashMap<>();
        Map<String, Long> byText = new LinkedHashMap<>();
        for (GoodsSpecification spec : specs) {
            byId.put(String.valueOf(spec.getId()), spec.getId());
            byText.putIfAbsent(spec.getValue(), spec.getId());
        }
        for (String token : raw) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String value = token.trim();
            Long id = byId.get(value);
            if (id == null) {
                id = byText.get(value);
            }
            if (id == null) {
                try {
                    // 临时序号：1 起顺序对应已创建的规格值
                    int index = Integer.parseInt(value);
                    if (index >= 1 && index <= specs.size()) {
                        id = specs.get(index - 1).getId();
                    }
                } catch (NumberFormatException ignore) {
                    // 非数字且不是文本 ⇒ 走下面的报错
                }
            }
            if (id == null) {
                throw GoodsApiException.paramInvalid("规格值不存在：" + value);
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    private String joinIds(List<Long> valueIds) {
        if (valueIds.isEmpty()) {
            return "";
        }
        List<String> texts = new ArrayList<>(valueIds.size());
        for (Long id : valueIds) {
            texts.add(String.valueOf(id));
        }
        return GoodsSupport.joinSpecValueIds(texts);
    }

    private List<String> specTexts(List<Long> valueIds, List<GoodsSpecification> specs) {
        Map<Long, String> textById = new HashMap<>();
        for (GoodsSpecification spec : specs) {
            textById.put(spec.getId(), spec.getValue());
        }
        List<String> texts = new ArrayList<>(valueIds.size());
        for (Long id : valueIds) {
            String text = textById.get(id);
            if (text != null) {
                texts.add(text);
            }
        }
        return texts;
    }

    /**
     * SKU 码：入参优先（须 ≤64 且全表唯一），缺省按 {@code goodsId-<值ID升序用_连接>} / {@code goodsId-D} 生成。
     */
    private String resolveSkuCode(Long goodsId, GoodsAdminDTO.SkuDTO sku, List<Long> valueIds) {
        if (sku.getSkuCode() != null && !sku.getSkuCode().isBlank()) {
            String code = sku.getSkuCode().trim();
            if (code.length() > MAX_SKU_CODE_LEN) {
                throw GoodsApiException.paramInvalid("SKU 编码最长 " + MAX_SKU_CODE_LEN + " 个字符");
            }
            return code;
        }
        List<String> ids = new ArrayList<>(valueIds.size());
        for (Long id : valueIds) {
            ids.add(String.valueOf(id));
        }
        return GoodsSupport.buildSkuCode(goodsId, ids);
    }

    private void assertSkuCodeUnique(String skuCode, Long selfId) {
        long count = goodsProductService.lambdaQuery()
                .eq(GoodsProduct::getSkuCode, skuCode)
                .eq(GoodsProduct::getDeleted, false)
                .ne(selfId != null, GoodsProduct::getId, selfId)
                .count();
        if (count > 0) {
            throw GoodsApiException.skuCodeDuplicated(skuCode);
        }
    }

    /* ==================== 公共字段与校验 ==================== */

    private void applyBaseFields(Goods goods, GoodsAdminDTO dto) {
        if (dto.getName() != null) {
            goods.setName(dto.getName().trim());
        }
        if (dto.getBrief() != null) {
            goods.setBrief(dto.getBrief().trim());
        }
        if (dto.getPicUrl() != null) {
            goods.setPicUrl(dto.getPicUrl().trim());
        }
        if (dto.getDetail() != null) {
            goods.setDetail(dto.getDetail());
        }
        if (dto.getKeywords() != null) {
            goods.setKeywords(dto.getKeywords().trim());
        }
        if (dto.getCategoryId() != null) {
            goods.setCategoryId(dto.getCategoryId());
        }
        if (dto.getBrandId() != null) {
            goods.setBrandId(dto.getBrandId());
        }
        if (dto.getSortOrder() != null) {
            goods.setSortOrder(dto.getSortOrder());
        }
        if (dto.getCounterPrice() != null) {
            goods.setCounterPrice(GoodsSupport.fenToYuan(dto.getCounterPrice()));
        }
        if (dto.getUnit() != null) {
            goods.setUnit(dto.getUnit().trim());
        }
        if (dto.getGallery() != null) {
            goods.setGallery(JSONUtil.toJsonStr(dto.getGallery()));
        }
    }

    private void validateBase(GoodsAdminDTO dto, boolean creating) {
        if (creating) {
            if (dto.getName() == null || dto.getName().isBlank()) {
                throw GoodsApiException.paramInvalid("商品名称不能为空");
            }
            if (dto.getPicUrl() == null || dto.getPicUrl().isBlank()) {
                throw GoodsApiException.paramInvalid("商品主图不能为空");
            }
            if (dto.getCategoryId() == null) {
                throw GoodsApiException.paramInvalid("商品类目不能为空");
            }
        }
        if (dto.getName() != null) {
            String name = dto.getName().trim();
            if (name.isEmpty() || name.length() > 120) {
                throw GoodsApiException.paramInvalid("商品名称必须为 1–120 字");
            }
        }
        if (dto.getBrief() != null && dto.getBrief().length() > 255) {
            throw GoodsApiException.paramInvalid("商品简介最长 255 字");
        }
        if (dto.getDetail() != null && dto.getDetail().length() > 20000) {
            throw GoodsApiException.paramInvalid("商品详情最长 20000 字");
        }
        if (dto.getKeywords() != null && dto.getKeywords().length() > 255) {
            throw GoodsApiException.paramInvalid("关键字最长 255 字");
        }
        if (dto.getUnit() != null && dto.getUnit().length() > 16) {
            throw GoodsApiException.paramInvalid("单位最长 16 字");
        }
        if (dto.getGallery() != null && dto.getGallery().size() > MAX_GALLERY) {
            throw GoodsApiException.paramInvalid("图集最多 " + MAX_GALLERY + " 张");
        }
        if (dto.getMinPrice() != null && dto.getMinPrice() < 1) {
            throw GoodsApiException.paramInvalid("售价必须大于 0");
        }
        if (dto.getCounterPrice() != null && dto.getCounterPrice() < 0) {
            throw GoodsApiException.paramInvalid("专柜价不能为负");
        }
        if (dto.getCategoryId() != null) {
            Category category = categoryService.getById(dto.getCategoryId());
            if (category == null || Boolean.TRUE.equals(category.getDeleted())) {
                throw GoodsApiException.paramInvalid("商品类目不存在");
            }
            long children = categoryService.lambdaQuery()
                    .eq(Category::getPid, dto.getCategoryId())
                    .eq(Category::getDeleted, false)
                    .count();
            if (children > 0) {
                throw GoodsApiException.paramInvalid("请选择末级类目");
            }
        }
    }

    /**
     * 状态解析：status 优先，其次 onSale；同时传且矛盾 ⇒ 400 STATUS_ON_SALE_CONFLICT。
     * 只传 onSale=false 时，编辑场景保留原「草稿」态，其余落到「已下架」。
     */
    private int resolveStatus(Integer status, Boolean onSale, Integer currentStatus) {
        if (status != null && (status < 0 || status > 2)) {
            throw GoodsApiException.paramInvalid("status 只能取 0 / 1 / 2");
        }
        if (status != null && onSale != null) {
            boolean expectOnSale = status == 1;
            if (expectOnSale != onSale) {
                throw GoodsApiException.statusOnSaleConflict();
            }
            return status;
        }
        if (status != null) {
            return status;
        }
        if (onSale != null) {
            if (onSale) {
                return 1;
            }
            return Integer.valueOf(0).equals(currentStatus) ? 0 : 2;
        }
        return currentStatus != null ? currentStatus : 0;
    }

    /**
     * 商品零售价跟随 SKU 最低价（列表无货时的兜底价，也是 admin 编辑页回显依据）。
     */
    private void syncRetailPrice(Long goodsId) {
        List<GoodsProduct> products = goodsProductService.lambdaQuery()
                .eq(GoodsProduct::getGoodsId, goodsId)
                .eq(GoodsProduct::getDeleted, false)
                .list();
        BigDecimal min = products.stream()
                .map(GoodsProduct::getPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (min == null) {
            return;
        }
        goodsService.lambdaUpdate()
                .eq(Goods::getId, goodsId)
                .set(Goods::getRetailPrice, min)
                .update();
    }
}
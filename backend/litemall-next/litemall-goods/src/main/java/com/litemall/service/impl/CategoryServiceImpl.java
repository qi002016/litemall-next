package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.cache.CacheService;
import com.litemall.domain.PO.Category;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.VO.CategoryVO;
import com.litemall.domain.dto.CategoryAdminDTO;
import com.litemall.domain.dto.CategoryAdminVO;
import com.litemall.domain.dto.CategoryCreateResultDTO;
import com.litemall.exception.GoodsApiException;
import com.litemall.mapper.CategoryMapper;
import com.litemall.mapper.GoodsMapper;
import com.litemall.service.ICategoryService;
import com.litemall.support.CatalogSupport;
import com.litemall.utils.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品分类表 服务实现类
 * </p>
 *
 * <p>
 * R2-01 新增管理端四个动作（CN-21~24 / 对内 {@code /inner/goods/admin/category/*}），
 * 约束来自 05（FROZEN）：
 * </p>
 * <ul>
 *   <li>最多两级：父级必须存在且自身是顶级，否则 400 {@code PARAM_INVALID}「分类最多两级」；</li>
 *   <li>同级唯一：只靠唯一键 {@code uk_category_name(pid,name)} 兜底，捕获
 *       {@link DuplicateKeyException} 转 409（<b>禁止先查后插</b>，并发下会穿透）；</li>
 *   <li>删除：有子分类 ⇒ 409 {@code CATEGORY_HAS_CHILDREN}；有商品（含已下架）⇒ 409
 *       {@code CATEGORY_IN_USE} +「请先移走商品」；否则<b>物理 DELETE</b>（05 §5.3 A）；</li>
 *   <li>修改：{@code pid} 冻结，{@code updateTime} 回传即 CAS，落空 ⇒ 409
 *       {@code CONCURRENT_MODIFIED}。</li>
 * </ul>
 * <p>
 * 商品数统计走 {@link GoodsMapper} 只读查询（{@code goods.category_id}），
 * 不经过 {@code IGoodsService}，避免与商品服务形成环。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    /** 消费端分类树缓存键（R2 写操作后必须失效，否则管理端改完前台 60s 内看不到） */
    private static final String TREE_CACHE_KEY = "category:tree";

    @Resource
    private CacheService cacheService;

    @Resource
    private GoodsMapper goodsMapper;

    @Override
    public List<CategoryVO> getCategoryTree() {
        return cacheService.getList(TREE_CACHE_KEY, CategoryVO.class, 3600, 300, () -> {
            List<Category> categories = lambdaQuery()
                    .eq(Category::getDeleted, false)
                    .orderByAsc(Category::getPid)
                    .orderByAsc(Category::getSortOrder)
                    .list();
            Map<Long, List<Category>> childrenMap = categories.stream()
                    .collect(Collectors.groupingBy(Category::getPid));
            return childrenMap.getOrDefault(0L, List.of()).stream()
                    .map(root -> buildTree(root, childrenMap))
                    .collect(Collectors.toList());
        });
    }

    private CategoryVO buildTree(Category category, Map<Long, List<Category>> childrenMap) {
        CategoryVO vo = BeanUtils.copyBean(category, CategoryVO.class);
        List<Category> children = childrenMap.get(category.getId());
        if (children != null && !children.isEmpty()) {
            vo.setChildren(children.stream()
                    .map(child -> buildTree(child, childrenMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }

    /* ==================== R2-01：管理端分类 ==================== */

    @Override
    public List<CategoryAdminVO> adminTree(boolean withGoodsCount) {
        // 管理端不走缓存：刚建完就要能查到（05 §8 第 1 步），且数据量只有几十行
        List<Category> all = lambdaQuery()
                .eq(Category::getDeleted, false)
                .orderByAsc(Category::getPid)
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId)
                .list();
        Map<Long, Long> goodsCounts = withGoodsCount ? goodsCountByCategory() : Map.of();

        Map<Long, List<Category>> childrenByPid = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : all) {
            long pid = pidOf(category);
            if (pid == 0L) {
                roots.add(category);
            } else {
                childrenByPid.computeIfAbsent(pid, key -> new ArrayList<>()).add(category);
            }
        }

        List<CategoryAdminVO> tree = new ArrayList<>(roots.size());
        for (Category root : roots) {
            CategoryAdminVO node = toAdminVO(root, goodsCounts, withGoodsCount);
            for (Category child : childrenByPid.getOrDefault(root.getId(), List.of())) {
                node.getChildren().add(toAdminVO(child, goodsCounts, withGoodsCount));
            }
            tree.add(node);
        }
        return tree;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryCreateResultDTO createCategory(CategoryAdminDTO dto) {
        if (dto == null) {
            throw GoodsApiException.paramInvalid("请求体不能为空");
        }
        String name = CatalogSupport.requiredText(dto.getName(), CatalogSupport.MAX_CATEGORY_NAME, "分类名称");
        String picUrl = CatalogSupport.optionalText(dto.getPicUrl(), CatalogSupport.MAX_URL, "分类图片");
        String iconUrl = CatalogSupport.optionalText(dto.getIconUrl(), CatalogSupport.MAX_URL, "分类图标");
        String keywords = CatalogSupport.optionalText(dto.getKeywords(), CatalogSupport.MAX_KEYWORDS, "分类关键词");
        String desc = CatalogSupport.optionalText(dto.getDesc(), CatalogSupport.MAX_DESC, "分类描述");
        int sortOrder = CatalogSupport.sortOrder(dto.getSortOrder(), 0);

        long pid = dto.getPid() == null ? 0L : dto.getPid();
        if (pid < 0L) {
            throw GoodsApiException.paramInvalid("父分类ID不能为负数");
        }
        if (pid != 0L) {
            Category parent = findActive(pid);
            if (parent == null) {
                throw GoodsApiException.parentCategoryNotFound();
            }
            // 父级自己必须是一级：否则会拼出三级树（05 §1.2 D：400 PARAM_INVALID，不单列错误码）
            if (pidOf(parent) != 0L) {
                throw GoodsApiException.paramInvalid("分类最多两级");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Category entity = new Category()
                .setPid(pid)
                .setName(name)
                .setKeywords(keywords == null ? "" : keywords)
                .setDesc(desc == null ? "" : desc)
                .setIconUrl(iconUrl == null ? "" : iconUrl)
                .setPicUrl(picUrl == null ? "" : picUrl)
                .setLevel(CatalogSupport.levelOf(pid))
                .setSortOrder(sortOrder)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        try {
            baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 唯一键 uk_category_name(pid,name) 兜底（05 §5.2：禁止先查后插）
            throw GoodsApiException.categoryNameDuplicated();
        }
        invalidateTree();
        return CategoryCreateResultDTO.of(entity.getId(), CatalogSupport.levelOf(pid));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(CategoryAdminDTO dto) {
        if (dto == null) {
            throw GoodsApiException.paramInvalid("请求体不能为空");
        }
        if (dto.getId() == null) {
            throw GoodsApiException.paramInvalid("分类ID不能为空");
        }
        Long id = dto.getId();
        Category existing = findActive(id);
        if (existing == null) {
            throw GoodsApiException.categoryNotFound();
        }
        // 05 §3.3：pid 冻结，显式传不同值直接拒绝（不做静默忽略）
        if (dto.getPid() != null && !dto.getPid().equals(pidOf(existing))) {
            throw GoodsApiException.paramInvalid("本期不支持调整上级分类");
        }

        Category update = new Category().setId(id);
        boolean changed = false;
        if (dto.getName() != null) {
            update.setName(CatalogSupport.requiredText(dto.getName(),
                    CatalogSupport.MAX_CATEGORY_NAME, "分类名称"));
            changed = true;
        }
        if (dto.getPicUrl() != null) {
            String picUrl = CatalogSupport.optionalText(dto.getPicUrl(), CatalogSupport.MAX_URL, "分类图片");
            update.setPicUrl(picUrl == null ? "" : picUrl);
            changed = true;
        }
        if (dto.getIconUrl() != null) {
            String iconUrl = CatalogSupport.optionalText(dto.getIconUrl(), CatalogSupport.MAX_URL, "分类图标");
            update.setIconUrl(iconUrl == null ? "" : iconUrl);
            changed = true;
        }
        if (dto.getKeywords() != null) {
            String keywords = CatalogSupport.optionalText(dto.getKeywords(),
                    CatalogSupport.MAX_KEYWORDS, "分类关键词");
            update.setKeywords(keywords == null ? "" : keywords);
            changed = true;
        }
        if (dto.getDesc() != null) {
            String desc = CatalogSupport.optionalText(dto.getDesc(), CatalogSupport.MAX_DESC, "分类描述");
            update.setDesc(desc == null ? "" : desc);
            changed = true;
        }
        if (dto.getSortOrder() != null) {
            update.setSortOrder(CatalogSupport.sortOrder(dto.getSortOrder(), 0));
            changed = true;
        }
        if (!changed) {
            // 只传 id + updateTime：等价于「无字段变更」，幂等成功（不写库、不误报并发）
            return;
        }
        update.setUpdateTime(LocalDateTime.now());

        LocalDateTime expect = CatalogSupport.parseUpdateTime(dto.getUpdateTime());
        var wrapper = Wrappers.<Category>lambdaUpdate()
                .eq(Category::getId, id)
                .eq(Category::getDeleted, false);
        if (expect != null) {
            wrapper.eq(Category::getUpdateTime, expect);
        }
        boolean updated;
        try {
            updated = update(update, wrapper);
        } catch (DuplicateKeyException e) {
            throw GoodsApiException.categoryNameDuplicated();
        }
        if (!updated) {
            // 读到了但改不动 ⇒ update_time 已被他人推进（05 §1.3 冻结提示语）
            throw GoodsApiException.concurrentModified("内容已被他人修改，请刷新后重试");
        }
        invalidateTree();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        if (id == null) {
            throw GoodsApiException.paramInvalid("分类ID不能为空");
        }
        if (findActive(id) == null) {
            throw GoodsApiException.categoryNotFound();
        }
        // 05 §3.4 第 2 步：仍有子分类（未删除）⇒ 409
        List<Category> childRows = lambdaQuery()
                .eq(Category::getPid, id)
                .eq(Category::getDeleted, false)
                .list();
        if (!childRows.isEmpty()) {
            throw GoodsApiException.categoryHasChildren();
        }
        // 05 §3.4 第 3 步：分类 ∪ 子分类下仍有商品，含已下架 ⇒ 409（子分类已在上一步拦下，
        // 这里仍按契约把自身与子分类一起查，防止并发新建子分类穿窗后漏判）
        List<Long> scopeIds = new ArrayList<>(childRows.size() + 1);
        scopeIds.add(id);
        childRows.forEach(child -> scopeIds.add(child.getId()));
        Long used = goodsMapper.selectCount(new QueryWrapper<Goods>()
                .eq("deleted", 0)
                .in("category_id", scopeIds));
        if (used != null && used > 0) {
            throw GoodsApiException.categoryInUse();
        }
        // 05 §5.3 A：物理删除（唯一键不含 deleted，软删会让同名分类永久不可重建）
        baseMapper.deleteById(id);
        invalidateTree();
    }

    /* ==================== R2-02：C 端分类过滤范围 ==================== */

    @Override
    public List<Long> categoryScopeIds(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        Category category = findActive(categoryId);
        if (category == null) {
            // 08 §1.1：传不存在/已删除分类 ⇒ 空列表（C 端列表返回空页，不报 404）
            return List.of();
        }
        List<Long> scope = new ArrayList<>(4);
        scope.add(category.getId());
        if (pidOf(category) == 0L) {
            // 一级分类展开为「自身 + 全部二级子分类」；末级只按等值过滤
            lambdaQuery()
                    .eq(Category::getPid, categoryId)
                    .eq(Category::getDeleted, false)
                    .orderByAsc(Category::getSortOrder)
                    .orderByAsc(Category::getId)
                    .list()
                    .forEach(child -> scope.add(child.getId()));
        }
        return scope;
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 查未删除分类。
     *
     * @param id 分类ID
     * @return 分类或 null
     */
    private Category findActive(Long id) {
        return lambdaQuery()
                .eq(Category::getId, id)
                .eq(Category::getDeleted, false)
                .one();
    }

    /**
     * 父分类ID（库列 NOT NULL，但历史脏数据可能为 null，统一按 0 处理）。
     *
     * @param category 分类
     * @return 父ID
     */
    private static long pidOf(Category category) {
        return category.getPid() == null ? 0L : category.getPid();
    }

    /**
     * 按分类统计商品数（含已下架，不含已删除）。
     *
     * @return categoryId → 商品数
     */
    private Map<Long, Long> goodsCountByCategory() {
        List<Map<String, Object>> rows = goodsMapper.selectMaps(new QueryWrapper<Goods>()
                .select("category_id", "COUNT(*) AS cnt")
                .eq("deleted", 0)
                .isNotNull("category_id")
                .groupBy("category_id"));
        Map<Long, Long> counts = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object categoryId = row.get("category_id");
            Object cnt = row.get("cnt");
            if (categoryId != null && cnt != null) {
                counts.put(((Number) categoryId).longValue(), ((Number) cnt).longValue());
            }
        }
        return counts;
    }

    private CategoryAdminVO toAdminVO(Category category, Map<Long, Long> goodsCounts, boolean withGoodsCount) {
        CategoryAdminVO vo = new CategoryAdminVO();
        vo.setId(category.getId());
        vo.setPid(pidOf(category));
        vo.setName(category.getName());
        vo.setLevel(CatalogSupport.levelOf(category.getPid()));
        vo.setSortOrder(category.getSortOrder());
        vo.setPicUrl(category.getPicUrl());
        vo.setIconUrl(category.getIconUrl());
        if (withGoodsCount) {
            vo.setGoodsCount(String.valueOf(goodsCounts.getOrDefault(category.getId(), 0L)));
        }
        return vo;
    }

    /**
     * 失效消费端分类树缓存。
     *
     * <p>
     * 必须在<b>事务提交后</b>执行（R2-10 D-C）：写入口都在 {@code @Transactional} 方法体内，
     * 若在提交前 {@code del}，并发读会用未提交的旧快照回源并写回 L2（TTL 300s），
     * 前台最长 5 分钟看不到刚提交的分类。事务同步激活时注册 {@code afterCommit}，
     * 回滚则不失效（数据没变，缓存仍有效）；无事务上下文时直接失效。
     * </p>
     *
     * <p>
     * 取消失败不影响已提交的事务结果（{@code CacheService.del} 内部 best-effort，异常不外抛）。
     * </p>
     */
    private void invalidateTree() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.del(TREE_CACHE_KEY);
                }
            });
            return;
        }
        cacheService.del(TREE_CACHE_KEY);
    }
}
package com.litemall.service;

import com.litemall.cache.CacheService;
import com.litemall.domain.dto.CategoryAdminDTO;
import com.litemall.domain.dto.CategoryCreateResultDTO;
import com.litemall.mapper.CategoryMapper;
import com.litemall.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * R2-10 缺陷回归 D-C：分类树缓存失效必须发生在<b>事务提交之后</b>。
 *
 * <p>
 * 验收现象：脚本第 1 步建分类、第 2 步立刻读 {@code /app/goods/category} 拿到 {@code root_children=[]}。
 * 根因是 {@code create/update/delete} 在 {@code @Transactional} 方法体内直接
 * {@code cacheService.del("category:tree")}：删除先于提交，并发读用未提交的旧快照回源，
 * 再把旧树写回 L2（TTL 300s），前台最长 5 分钟看不到新分类。
 * </p>
 *
 * <p>
 * 本测试用真实 MySQL + {@link MockitoBean} 的 {@link CacheService} 观察失效时机：
 * 事务回调内断言「还没删」，事务提交返回后断言「删了且只删一次」；回滚则「一次都不删」。
 * </p>
 */
@SpringBootTest
class CategoryTreeCacheInvalidationTest {

    /** 消费端分类树缓存键（与 {@code CategoryServiceImpl.TREE_CACHE_KEY} 同值） */
    private static final String TREE_CACHE_KEY = "category:tree";

    /** 每次 JVM 执行唯一的名称后缀：唯一键 uk_category_name(pid,name) 含 name */
    private static final String SUFFIX = Long.toString(System.currentTimeMillis() % 1_000_000);

    @MockitoBean
    private CacheService cacheService;

    @Autowired
    private ICategoryService categoryService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdIds.forEach(id -> categoryMapper.deleteById(id));
        createdIds.clear();
    }

    @Test
    @DisplayName("D-C：create 先注册 afterCommit —— 事务内不失效、提交后失效一次")
    void createInvalidatesAfterCommit() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Long id = template.execute(status -> {
            CategoryCreateResultDTO created = categoryService.createCategory(category("缓存时机-新建"));
            verify(cacheService, never()).del(anyString());
            return created.getId();
        });

        verify(cacheService, times(1)).del(TREE_CACHE_KEY);
        assertThatId(id);
    }

    @Test
    @DisplayName("D-C：update 同样只在提交后失效")
    void updateInvalidatesAfterCommit() {
        Long id = categoryService.createCategory(category("缓存时机-改前")).getId();
        createdIds.add(id);
        reset(cacheService);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            CategoryAdminDTO dto = new CategoryAdminDTO();
            dto.setId(id);
            dto.setName("缓存时机-改后" + SUFFIX);
            categoryService.updateCategory(dto);
            verify(cacheService, never()).del(anyString());
        });

        verify(cacheService, times(1)).del(TREE_CACHE_KEY);
    }

    @Test
    @DisplayName("D-C：delete 同样只在提交后失效")
    void deleteInvalidatesAfterCommit() {
        Long id = categoryService.createCategory(category("缓存时机-删除")).getId();
        reset(cacheService);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            categoryService.deleteCategory(id);
            verify(cacheService, never()).del(anyString());
        });

        verify(cacheService, times(1)).del(TREE_CACHE_KEY);
    }

    @Test
    @DisplayName("D-C：事务回滚 ⇒ 一次都不失效（数据没变，缓存仍有效）")
    void rollbackDoesNotInvalidate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.executeWithoutResult(status -> {
            categoryService.createCategory(category("缓存时机-回滚"));
            status.setRollbackOnly();
        });

        verify(cacheService, never()).del(anyString());
    }

    @Test
    @DisplayName("D-C：无事务上下文 ⇒ 立即失效（保留旧行为，供非事务调用方使用）")
    void invalidatesImmediatelyWithoutTransaction() throws Exception {
        CategoryServiceImpl target = AopTestUtils.getUltimateTargetObject(categoryService);
        Method method = CategoryServiceImpl.class.getDeclaredMethod("invalidateTree");
        method.setAccessible(true);
        method.invoke(target);

        verify(cacheService, times(1)).del(TREE_CACHE_KEY);
    }

    /* ==================== 夹具 ==================== */

    private CategoryAdminDTO category(String prefix) {
        CategoryAdminDTO dto = new CategoryAdminDTO();
        dto.setName(prefix + SUFFIX);
        dto.setSortOrder(0);
        return dto;
    }

    private void assertThatId(Long id) {
        if (id == null || categoryMapper.selectById(id) == null) {
            throw new AssertionError("事务提交后分类必须落库：" + id);
        }
        createdIds.add(id);
    }
}
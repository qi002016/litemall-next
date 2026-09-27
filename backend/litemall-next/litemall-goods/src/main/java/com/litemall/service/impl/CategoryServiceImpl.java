package com.litemall.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.cache.CacheService;
import com.litemall.domain.PO.Category;
import com.litemall.domain.VO.CategoryVO;
import com.litemall.mapper.CategoryMapper;
import com.litemall.service.ICategoryService;
import com.litemall.utils.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品分类表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    @Resource
    private CacheService cacheService;

    @Override
    public List<CategoryVO> getCategoryTree() {
        return cacheService.getList("category:tree", CategoryVO.class, 3600, 300, () -> {
            List<Category> categories = lambdaQuery()
                    .eq(Category::getDeleted, false)
                    .orderByAsc(Category::getPid)
                    .orderByAsc(Category::getSortOrder)
                    .list();
            Map<Integer, List<Category>> childrenMap = categories.stream()
                    .collect(Collectors.groupingBy(Category::getPid));
            return childrenMap.getOrDefault(0, List.of()).stream()
                    .map(root -> buildTree(root, childrenMap))
                    .collect(Collectors.toList());
        });
    }

    private CategoryVO buildTree(Category category, Map<Integer, List<Category>> childrenMap) {
        CategoryVO vo = BeanUtils.copyBean(category, CategoryVO.class);
        List<Category> children = childrenMap.get(category.getId());
        if (children != null && !children.isEmpty()) {
            vo.setChildren(children.stream()
                    .map(child -> buildTree(child, childrenMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }
}

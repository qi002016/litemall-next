package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Category;
import com.litemall.domain.VO.CategoryVO;

import java.util.List;

/**
 * <p>
 * 商品分类表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
public interface ICategoryService extends IService<Category> {

    /**
     * 获取商品分类树(顶级分类 + 子分类)
     *
     * @return 分类树
     */
    List<CategoryVO> getCategoryTree();
}

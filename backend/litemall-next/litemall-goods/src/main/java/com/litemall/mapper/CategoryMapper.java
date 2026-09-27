package com.litemall.mapper;

import com.litemall.domain.PO.Category;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 商品分类表 Mapper 接口
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

}

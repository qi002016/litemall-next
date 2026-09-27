package com.litemall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.PO.Brand;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.BrandVO;
import com.litemall.mapper.BrandMapper;
import com.litemall.service.IBrandService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 品牌商表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Service
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements IBrandService {

    @Override
    public PageVO<BrandVO> getBrandList(int pageNum, int pageSize) {
        Page<Brand> page = new Page<>(pageNum, pageSize);
        Page<Brand> p = lambdaQuery()
                .eq(Brand::getDeleted, false)
                .orderByAsc(Brand::getSortOrder)
                .orderByDesc(Brand::getAddTime)
                .page(page);
        return PageVO.of(p, BrandVO.class);
    }
}

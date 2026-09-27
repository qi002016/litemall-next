package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Brand;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.BrandVO;

/**
 * <p>
 * 品牌商表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
public interface IBrandService extends IService<Brand> {

    /**
     * 分页获取品牌列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 品牌分页
     */
    PageVO<BrandVO> getBrandList(int pageNum, int pageSize);

}

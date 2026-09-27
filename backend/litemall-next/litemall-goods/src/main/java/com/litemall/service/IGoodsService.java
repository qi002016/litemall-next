package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.GoodsDetailVO;
import com.litemall.domain.VO.GoodsVO;

/**
 * <p>
 * 商品基本信息表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
public interface IGoodsService extends IService<Goods> {

    /**
     * 分页查询商品列表
     */
    PageVO<GoodsVO> getGoodsList(int pageNum, int pageSize, Long categoryId, String keyword, Long brandId);


    /**
     * 获取商品详情
     */
    GoodsDetailVO getGoodsDetail(Long goodsId);
}

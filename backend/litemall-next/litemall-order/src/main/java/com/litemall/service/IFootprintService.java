package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Footprint;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.FootprintVO;

/**
 * <p>
 * 用户浏览足迹表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public interface IFootprintService extends IService<Footprint> {

    /**
     * 我的足迹列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页足迹列表
     */
    PageVO<FootprintVO> listFootprint(Integer pageNum, Integer pageSize);

    /**
     * 记录浏览足迹（同商品保留最近一次，浏览商品详情时调用）
     *
     * @param goodsId 商品ID
     */
    void addFootprint(Long goodsId);
}

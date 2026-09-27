package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Ad;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.AdVO;
import com.litemall.domain.VO.CouponVO;

import java.util.List;

/**
 * <p>
 * 广告表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-23
 */
public interface IAdService extends IService<Ad> {

    /**
     * 获取广告列表
     * @return 广告列表
     */
    List<AdVO> getAdList(String position);

}

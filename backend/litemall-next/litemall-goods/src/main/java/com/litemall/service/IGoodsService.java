package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.GoodsAdminQuery;
import com.litemall.domain.DTO.GoodsListQuery;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.GoodsDetailVO;
import com.litemall.domain.VO.GoodsVO;
import com.litemall.domain.dto.GoodsAdminDTO;

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
     * C 端商品分页列表（08 v4 CI-31）。
     *
     * @param query 已归一的查询条件
     * @return 商品行（含 displayPrice / totalStock / soldOut）
     */
    PageVO<GoodsVO> getGoodsList(GoodsListQuery query);

    /**
     * C 端商品详情（08 v4 CI-32）。
     *
     * @param goodsId 商品ID
     * @return 商品详情（specs / skus / availableStock / canBuy）
     */
    GoodsDetailVO getGoodsDetail(Long goodsId);

    /* ==================== H5：管理端商品（admin 服务经 Feign 调用，不再直写 litemall_goods） ==================== */

    /**
     * 管理端商品分页列表（08 v4 CI-09 / 对内 CI-47）。
     *
     * @param query 已归一的查询条件
     * @return 分页商品行
     */
    PageVO<GoodsAdminDTO> adminListGoods(GoodsAdminQuery query);

    /**
     * 管理端商品总数（首页统计）
     *
     * @return 未删除商品数
     */
    long adminCountGoods();
}
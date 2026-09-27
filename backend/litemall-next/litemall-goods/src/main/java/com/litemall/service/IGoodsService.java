package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
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
     * 分页查询商品列表
     */
    PageVO<GoodsVO> getGoodsList(int pageNum, int pageSize, Long categoryId, String keyword, Long brandId);


    /**
     * 获取商品详情
     */
    GoodsDetailVO getGoodsDetail(Long goodsId);

    /* ==================== H5：管理端商品（admin 服务经 Feign 调用，不再直写 litemall_goods） ==================== */

    /**
     * H5：管理端商品分页列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param name     商品名称模糊匹配，可空
     * @return 分页商品行（字段与改造前 admin 端返回一致）
     */
    PageVO<GoodsAdminDTO> adminListGoods(Integer pageNum, Integer pageSize, String name);

    /**
     * H5：管理端商品总数（首页统计）
     *
     * @return 未删除商品数
     */
    long adminCountGoods();

    /**
     * H5：管理端新建商品（与改造前 admin 端落库字段一致：上架、未删除、写入创建/更新时间）
     *
     * @param dto 商品信息
     * @return 新建商品ID
     */
    Long adminCreateGoods(GoodsAdminDTO dto);

    /**
     * H5：管理端更新商品（仅更新非空字段），并失效商品详情缓存
     *
     * @param dto 商品信息（id 必填）
     */
    void adminUpdateGoods(GoodsAdminDTO dto);

    /**
     * H5：管理端逻辑删除商品，并失效商品详情缓存
     *
     * @param id 商品ID
     */
    void adminDeleteGoods(Integer id);
}

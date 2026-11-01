package com.litemall.service;

import com.litemall.domain.dto.GoodsAdminDTO;

/**
 * <p>
 * 商品写路径（08 v4 CI-10 新建 / CI-11 编辑 / CI-12 删除）。
 * </p>
 *
 * <p>
 * 与读路径分接口，是因为写路径承担规格矩阵重建、SKU 全量覆盖、并发 CAS 与引用校验，
 * 逻辑量远大于读；拆开后读路径可以保持"无事务、可缓存"的简单形态。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
public interface IGoodsWriteService {

    /**
     * 新建商品（含规格维度与 SKU 矩阵，同库本地事务）。
     *
     * @param dto 商品写入数据
     * @return 新商品ID
     */
    Long adminCreateGoods(GoodsAdminDTO dto);

    /**
     * 编辑商品（未传字段保持原值；skus 传了即全量覆盖；updateTime 用于并发 CAS）。
     *
     * @param dto 商品写入数据
     */
    void adminUpdateGoods(GoodsAdminDTO dto);

    /**
     * 删除商品（仅草稿且无订单引用；否则 409 GOODS_IN_USE）。
     *
     * @param id 商品ID
     */
    void adminDeleteGoods(Long id);
}
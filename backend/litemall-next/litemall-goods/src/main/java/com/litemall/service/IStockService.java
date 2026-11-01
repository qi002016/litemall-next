package com.litemall.service;

import com.litemall.domain.DTO.StockAdminQuery;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.DeductBatchResultDTO;
import com.litemall.domain.dto.ReleasedBatchResultDTO;
import com.litemall.domain.dto.StockAdminDTO;
import com.litemall.domain.dto.StockChangeDTO;

/**
 * <p>
 * 库存服务：管理端库存视图与调整（08 v4 CN-13 / CN-14）＋ 订单侧库存扣减/回补（CI-42/43/44/45）。
 * </p>
 *
 * <p>
 * 三量口径（08 §2.1）：{@code number} 是唯一持久化库存；{@code locked} 由 order 域 CN-IN-04 派生；
 * {@code sellable = max(0, number − locked)}。goods 域<b>不存</b> locked，任何"库存不足"的判定
 * 都只针对 {@code number}（下单占用由 order 域先锁后扣控制）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
public interface IStockService {

    /**
     * 管理端库存分页列表（CN-13）。
     *
     * @param query 查询条件
     * @return 分页库存行
     */
    PageVO<StockAdminDTO> adminStockList(StockAdminQuery query);

    /**
     * 管理端库存调整（CN-14）：number 置为绝对值，CAS 更新，不允许低于 locked。
     *
     * @param dto 调整入参（productId/number/remark/expectNumber）
     * @return 调整后的库存行
     */
    StockAdminDTO adminStockUpdate(StockAdminDTO dto);

    /**
     * 单条扣减（CI-42）：条件更新 {@code number >= n}，不足抛 400「库存不足」。
     *
     * @param productId 货品ID
     * @param number    扣减数量
     */
    void deduct(Long productId, Integer number);

    /**
     * 单条回补（CI-43）：{@code number += n}，作为售后退款回补；goods 侧不做幂等（幂等在 order 侧）。
     *
     * @param productId 货品ID
     * @param number    回补数量
     */
    void release(Long productId, Integer number);

    /**
     * 批量扣减（CI-44）：按 productId 升序扣减避免死锁，任一行不足则整批回滚并抛
     * {@link com.litemall.exception.InsufficientStockException}（携带 shortage 明细）。
     *
     * @param dto 批量变更
     * @return 全部成功的结果
     */
    DeductBatchResultDTO deductBatch(StockChangeDTO dto);

    /**
     * 批量回补（CI-45）：逐行 {@code number += n}，缺货行标记 released=false 但不报错。
     *
     * @param dto 批量变更
     * @return 回补结果
     */
    ReleasedBatchResultDTO releaseBatch(StockChangeDTO dto);
}
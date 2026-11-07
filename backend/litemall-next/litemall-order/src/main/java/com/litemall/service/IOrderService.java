package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.OrderConfirmDTO;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.OrderConfirmVO;
import com.litemall.domain.VO.OrderDetailVO;
import com.litemall.domain.VO.OrderListItemVO;
import com.litemall.domain.VO.OrderSubmitVO;
import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.domain.dto.LockedStockDTO;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminDetailDTO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.domain.dto.OrderCloseDTO;
import com.litemall.domain.dto.OrderGoodsStatusDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.domain.dto.OrderTryCompleteDTO;
import com.litemall.domain.dto.OrderTryCompleteResultDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import com.litemall.domain.dto.PaySuccessResultDTO;

/**
 * <p>
 * 订单表 服务类（R1：订单状态机、锁量、三种关闭、后台订单域的唯一收口点）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public interface IOrderService extends IService<Order> {

    /* ==================== C 端 ==================== */

    /**
     * 申请下单幂等token（写入 Redis，TTL 5 分钟）
     *
     * @return 幂等token
     */
    String createIdemToken();

    /**
     * CI-80 订单确认页试算（只读，无状态变更、不占幂等键）。
     *
     * @param dto cartIds 或 buyNow 二选一 + addressId + couponId?
     * @return 金额汇总 + 行明细（含券额分摊与可售提示）
     */
    OrderConfirmVO confirmOrder(OrderConfirmDTO dto);

    /**
     * CI-82 提交订单：只落订单行并<b>锁定可售量</b>（不动 goods_product.number），
     * 券额按行分摊落 order_goods.coupon_share/actual_amount，expire_time = 下单 + 30 分钟。
     *
     * @param dto 提交订单参数
     * @return 订单结果（含 expireTime）
     */
    OrderSubmitVO submitOrder(OrderSubmitDTO dto);

    /**
     * CI-83 我的订单列表（7 栏 + status 多值 + 排序）。
     *
     * @param tab       all/unpaid/undelivered/shipped/unreviewed/finished/aftersale
     * @param status    状态标识多值逗号；与 tab 同传时 tab 优先
     * @param pageNo    页码
     * @param pageSize  每页条数（默认 20，上限 100）
     * @param sortBy    addTime（默认）| orderAmount
     * @param isAsc     是否升序（默认 false）
     * @return 分页订单列表
     */
    PageVO<OrderListItemVO> listOrder(String tab, String status, Integer pageNo, Integer pageSize,
                                      String sortBy, Boolean isAsc);

    /**
     * CI-84 订单详情（他人订单按 404）。
     *
     * @param orderId 订单ID
     * @return 订单详情
     */
    OrderDetailVO getOrderDetail(Long orderId);

    /**
     * CI-85 取消订单：101 → 103（CAS）+ close_type=1 + end_time + 释放锁量/券。
     *
     * @param orderId 订单ID
     */
    void cancelOrder(Long orderId);

    /**
     * CI-86 确认收货：201 → 301（CAS）+ confirm_time。
     *
     * @param orderId 订单ID
     */
    void confirmReceive(Long orderId);

    /**
     * CN-20 删除订单（C 端隐藏）：user_deleted 0→1（幂等），有未完结售后 → 409。
     *
     * @param orderId 订单ID
     */
    void hideOrder(Long orderId);

    /* ==================== 管理端（admin / 调度 → order） ==================== */

    /**
     * CI-26 / CI-99 管理端订单分页列表（同构）。
     */
    PageVO<OrderAdminDTO> adminListOrders(Integer pageNo, Integer pageSize, String status, String aftersaleStatus,
                                          String orderSn, String keyword, Long userId,
                                          String addTimeMin, String addTimeMax, String sortBy, Boolean isAsc);

    /**
     * CN-33 管理端订单详情（含行级售后摘要、shipUpdateCount/canUpdateShip）。
     *
     * @param id 订单ID
     * @return 详情
     */
    OrderAdminDetailDTO adminOrderDetail(Long id);

    /**
     * CI-100 管理端订单统计（R2 改契约：新增统计期入参，出参只增字段）。
     * <p>
     * 口径见 05-R2 实施口径 §2.1.3（SQL-1..SQL-5 逐字冻结）：GMV/订单数按 {@code pay_time}
     * 归期且 {@code order_status NOT IN (101,103)}；退款按 {@code litemall_refund.refund_time}、
     * {@code status=1}；{@code waitShipCount}/{@code pendingAftersaleCount} 为当前存量；
     * {@code todayOrders} 为今日支付成功订单数（不随统计期变化）。
     * </p>
     *
     * @param period 统计期 {@code today|7d|30d}，可空；缺省/非法一律落 {@code 30d}（不报错）
     * @return 统计值（金额为整数分）
     */
    OrderAdminStatsDTO adminOrderStats(String period);

    /**
     * CI-101 管理端发货：102 → 201（CAS），重复发货 409。
     *
     * @param dto 订单ID + 快递信息
     */
    void adminShip(OrderShipDTO dto);

    /**
     * CN-10 运营立即关单：101 → 103 + close_type=3 + 日志 + 释放。
     *
     * @param dto 订单ID + 原因 + 操作管理员
     */
    void adminClose(OrderCloseDTO dto);

    /**
     * CN-11 超时关单（逐单 CAS 101 AND expire_time&lt;=now → 103 + close_type=2）。
     *
     * @param limit  单批上限 1–200
     * @param dryRun 只扫描不落库
     * @return 逐单结果
     */
    CloseExpiredResultDTO adminCloseExpired(Integer limit, Boolean dryRun);

    /**
     * R4-MQ 单笔超时关单（延时消息消费端调用，幂等）。
     * <p>仅当订单存在、{@code order_status=101} 且 {@code now >= expire_time} 时执行
     * {@code 101 → 103}（close_type=2，日志 action={@code order:close-expired}）；
     * 其余情况（不存在 / 已支付 / 已关闭 / 未到期 / 并发被抢）一律返回 false，不抛异常。</p>
     *
     * @param orderId 订单 ID
     * @return true = 本次真的关闭了该单
     */
    boolean closeExpiredOrder(Long orderId);

    /**
     * CN-12 修改物流：仅 201 且改过 0 次（litemall_log 计数）。
     *
     * @param dto 订单ID + 新快递信息 + 原因
     */
    void adminUpdateShip(OrderShipDTO dto);

    /* ==================== 对内（pay / message / goods） ==================== */

    /**
     * CI-97 支付成功：101 → 102（CAS）+ 一次性实扣库存 + 幂等（pay_time 闸门）。
     *
     * @param dto 订单ID + 支付单号
     * @return applied/deducted/shortage（不抛业务异常）
     */
    PaySuccessResultDTO markPaid(PaySuccessDTO dto);

    /**
     * CN-IN-01 订单行评价校验（只读）。
     *
     * @param id     订单行ID
     * @param userId 可选；传入且不匹配 → 404
     * @return 行状态
     */
    OrderGoodsStatusDTO orderGoodsStatus(Long id, Long userId);

    /**
     * CN-IN-08 订单尝试完成（R2 新增；goods 域 CI-36 评价落库后调用）。
     * <p>
     * 幂等语义：先标记订单行已评价，再按三条件 CAS {@code 301 → 501}；
     * 重复调用或已非 301 ⇒ {@code changed=false} + 200（不报 409）。
     * 未发货/未收货等非 301 状态一律不做跃迁（不允许跨状态跳 501）。
     * </p>
     *
     * @param orderId 订单ID（路径参数）
     * @param dto     订单行ID + 评价ID（均必填）
     * @return 订单ID + 回读状态 + 是否发生跃迁
     */
    OrderTryCompleteResultDTO tryCompleteOrder(Long orderId, OrderTryCompleteDTO dto);

    /**
     * CN-IN-04 查询 SKU 锁定量（{@code order_status=101 AND og.deleted=0} 的 number 之和）。
     *
     * @param productIds 逗号分隔，≤200；空表示全部有占用
     * @return 按 productId 升序的锁定量
     */
    LockedStockDTO lockedStock(String productIds);
}
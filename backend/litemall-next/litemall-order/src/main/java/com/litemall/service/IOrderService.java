package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.OrderDetailVO;
import com.litemall.domain.VO.OrderListItemVO;
import com.litemall.domain.VO.OrderSubmitVO;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.domain.dto.OrderRefundDTO;
import com.litemall.domain.dto.OrderShipDTO;

/**
 * <p>
 * 订单表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public interface IOrderService extends IService<Order> {

    /**
     * 申请下单幂等token（写入 Redis，TTL 5 分钟）
     *
     * @return 幂等token
     */
    String createIdemToken();

    /**
     * 提交订单（Seata 全局事务：扣库存 + 建订单 + 核销优惠券 + 清购物车）
     *
     * @param dto 提交订单参数
     * @return 订单结果
     */
    OrderSubmitVO submitOrder(OrderSubmitDTO dto);

    /**
     * 我的订单列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   unpaid/undelivered/shipped/confirmed/refund
     * @return 分页订单列表
     */
    PageVO<OrderListItemVO> listOrder(Integer pageNum, Integer pageSize, String status);

    /**
     * 订单详情
     *
     * @param orderId 订单ID
     * @return 订单详情
     */
    OrderDetailVO getOrderDetail(Long orderId);

    /**
     * 取消订单（仅 unpaid 可取消；释放库存与优惠券）
     *
     * @param orderId 订单ID
     */
    void cancelOrder(Long orderId);

    /**
     * 确认收货（仅 shipped 可操作，变为 confirmed）
     *
     * @param orderId 订单ID
     */
    void confirmReceive(Long orderId);

    /* ==================== H5：管理端订单（admin 服务经 Feign 调用，不再直写 litemall_order） ==================== */

    /**
     * H5：管理端订单分页列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   文本状态 unpaid/undelivered/shipped/confirmed/refund，空为全部
     * @return 分页订单行（字段与改造前 admin 端返回一致）
     */
    PageVO<OrderAdminDTO> adminListOrders(Integer pageNum, Integer pageSize, String status);

    /**
     * H5：管理端首页订单统计
     *
     * @return 今日订单数 + 待发货订单数
     */
    OrderAdminStatsDTO adminOrderStats();

    /**
     * H5：管理端发货（状态机：仅 102 待发货 → 201 已发货；其余状态一律拒绝）
     *
     * @param dto 订单ID + 快递信息
     */
    void adminShip(OrderShipDTO dto);

    /**
     * H5：管理端退款（状态机：102/201/301 → 401 售后，并复用取消订单的库存回补 + 券回退；
     * 401 视为幂等直接返回；101/103/402 拒绝）
     *
     * @param dto 订单ID + 退款金额/备注
     */
    void adminRefund(OrderRefundDTO dto);
}

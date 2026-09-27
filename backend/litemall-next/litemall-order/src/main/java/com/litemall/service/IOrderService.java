package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.OrderDetailVO;
import com.litemall.domain.VO.OrderListItemVO;
import com.litemall.domain.VO.OrderSubmitVO;

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
}

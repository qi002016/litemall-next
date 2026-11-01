package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.domain.dto.LockedStockDTO;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminDetailDTO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.domain.dto.OrderCloseDTO;
import com.litemall.domain.dto.OrderGoodsStatusDTO;
import com.litemall.domain.dto.OrderPayInfoDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.domain.dto.OrderTryCompleteDTO;
import com.litemall.domain.dto.OrderTryCompleteResultDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import com.litemall.domain.dto.PaySuccessResultDTO;
import com.litemall.mapper.OrderMapper;
import com.litemall.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 订单服务内部接口（供 pay / admin / goods / message 服务 Feign 调用，不走网关鉴权）。
 * </p>
 * <p>
 * 契约（07 §6）：<b>对内接口不加 {@code Result} 包装</b>，失败保留非 200 HTTP 状态，
 * 让 Feign 调用方能感知（03 §4.1）；错误码用 {@code RESOURCE_NOT_FOUND}/{@code ORDER_STATUS_ILLEGAL} 等登记码。
 * </p>
 */
@RestController
@RequestMapping("/inner/order")
@RequiredArgsConstructor
@Tag(name = "订单内部接口", description = "供服务间 Feign 调用的订单接口")
public class OrderInnerController {

    private final OrderMapper orderMapper;
    private final IOrderService orderService;

    /* ==================== 支付域（pay 服务调用） ==================== */

    @Operation(summary = "查询订单支付信息（CI-96）")
    @GetMapping("/pay/info")
    public OrderPayInfoDTO getPayInfo(@RequestParam("orderId") Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getDeleted, false));
        if (order == null) {
            return null;
        }
        OrderPayInfoDTO dto = new OrderPayInfoDTO();
        dto.setOrderId(order.getId());
        dto.setUserId(order.getUserId());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setActualPrice(order.getActualPrice());
        dto.setPayTime(order.getPayTime());
        dto.setPayId(order.getPayId());
        return dto;
    }

    @Operation(summary = "支付成功（CI-97）：101 → 102 + 一次性实扣库存，实扣失败仍返回 200")
    @PostMapping("/pay/success")
    public PaySuccessResultDTO markPaid(@RequestBody PaySuccessDTO dto) {
        return orderService.markPaid(dto);
    }

    /* ==================== 管理端订单（admin 服务调用） ==================== */

    @Operation(summary = "管理端订单分页列表（CI-26 / CI-99）")
    @GetMapping("/admin/list")
    public PageVO<OrderAdminDTO> adminList(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "aftersaleStatus", required = false) String aftersaleStatus,
            @RequestParam(value = "orderSn", required = false) String orderSn,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "addTimeMin", required = false) String addTimeMin,
            @RequestParam(value = "addTimeMax", required = false) String addTimeMax,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "isAsc", required = false) Boolean isAsc) {
        return orderService.adminListOrders(pageNo, pageSize, status, aftersaleStatus, orderSn, keyword,
                userId, addTimeMin, addTimeMax, sortBy, isAsc);
    }

    @Operation(summary = "管理端订单详情（CN-33）")
    @GetMapping("/admin/order/{id}")
    public OrderAdminDetailDTO adminOrderDetail(@PathVariable("id") Long id) {
        return orderService.adminOrderDetail(id);
    }

    @Operation(summary = "管理端订单统计（CI-100）：period=today|7d|30d，缺省 30d")
    @GetMapping("/admin/stats")
    public OrderAdminStatsDTO adminStats(
            @RequestParam(value = "period", required = false) String period) {
        return orderService.adminOrderStats(period);
    }

    @Operation(summary = "管理端发货（CI-101）：102 → 201")
    @PostMapping("/admin/ship")
    public void adminShip(@RequestBody OrderShipDTO dto) {
        orderService.adminShip(dto);
    }

    @Operation(summary = "运营立即关单（CN-10）：101 → 103 + 释放")
    @PostMapping("/admin/close")
    public void adminClose(@RequestBody OrderCloseDTO dto) {
        orderService.adminClose(dto);
    }

    @Operation(summary = "超时关单（CN-11，调度每分钟调用）")
    @PostMapping("/admin/close-expired")
    public CloseExpiredResultDTO adminCloseExpired(@RequestParam(value = "limit", required = false) Integer limit,
                                                   @RequestParam(value = "dryRun", required = false) Boolean dryRun) {
        return orderService.adminCloseExpired(limit, dryRun);
    }

    @Operation(summary = "修改物流单号（CN-12）：仅已发货且改过 0 次")
    @PostMapping("/admin/update-ship")
    public void adminUpdateShip(@RequestBody OrderShipDTO dto) {
        orderService.adminUpdateShip(dto);
    }

    /* ==================== 跨域只读（message / goods 服务调用） ==================== */

    @Operation(summary = "订单行评价校验（CN-IN-01）")
    @GetMapping("/admin/order-goods/{id}")
    public OrderGoodsStatusDTO orderGoodsStatus(@PathVariable("id") Long id,
                                                @RequestParam(value = "userId", required = false) Long userId) {
        return orderService.orderGoodsStatus(id, userId);
    }

    @Operation(summary = "订单尝试完成 301→501（CN-IN-08，goods 评价成功后调用；幂等）")
    @PostMapping("/admin/order/{orderId}/try-complete")
    public OrderTryCompleteResultDTO tryCompleteOrder(@PathVariable("orderId") Long orderId,
                                                      @RequestBody OrderTryCompleteDTO body) {
        return orderService.tryCompleteOrder(orderId, body);
    }

    @Operation(summary = "查询 SKU 锁定量（CN-IN-04）")
    @GetMapping("/locked-stock")
    public LockedStockDTO lockedStock(@RequestParam(value = "productIds", required = false) String productIds) {
        return orderService.lockedStock(productIds);
    }
}
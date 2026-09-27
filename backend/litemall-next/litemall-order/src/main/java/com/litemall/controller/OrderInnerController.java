package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litemall.domain.PO.Order;
import com.litemall.domain.dto.OrderPayInfoDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.OrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * <p>
 * 订单服务内部接口（供 pay 服务 Feign 调用, 不走网关鉴权）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@RestController
@RequestMapping("/inner/order")
@RequiredArgsConstructor
@Tag(name = "订单内部接口", description = "供服务间 Feign 调用的订单接口")
public class OrderInnerController {

    /** 订单状态：101 未付款；102 待发货；301 已收货；401 售后 */
    private static final int STATUS_UNPAID = 101;
    private static final int STATUS_UNDELIVERED = 102;
    private static final int STATUS_CONFIRMED = 301;
    private static final int STATUS_REFUND = 401;

    private final OrderMapper orderMapper;

    @Operation(summary = "查询订单支付信息")
    @GetMapping("/pay/info")
    public OrderPayInfoDTO getPayInfo(@RequestParam("orderId") Long orderId) {
        Order order = orderMapper.selectById(orderId.intValue());
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            return null;
        }
        OrderPayInfoDTO dto = new OrderPayInfoDTO();
        dto.setOrderId(order.getId().longValue());
        dto.setUserId(order.getUserId().longValue());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setActualPrice(order.getActualPrice());
        dto.setPayTime(order.getPayTime());
        dto.setPayId(order.getPayId());
        return dto;
    }

    @Operation(summary = "支付成功：订单 101 → 102 并写支付时间/单号")
    @PostMapping("/pay/success")
    public void markPaid(@RequestBody PaySuccessDTO dto) {
        Order order = orderMapper.selectById(dto.getOrderId().intValue());
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            throw new BadRequestException("订单不存在");
        }
        if (order.getOrderStatus() != null && order.getOrderStatus() != STATUS_UNPAID) {
            // H2：幂等——同一支付单号的重复回调（含微信重试）直接视为成功，不再改状态
            if (order.getOrderStatus() == STATUS_UNDELIVERED
                    && Objects.equals(order.getPayId(), dto.getPayId())) {
                return;
            }
            throw new BadRequestException("订单状态不允许支付");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus(STATUS_UNDELIVERED);
        order.setPayId(dto.getPayId());
        order.setPayTime(now);
        order.setUpdateTime(now);
        orderMapper.updateById(order);
    }

    @Operation(summary = "退款申请：订单置为售后状态并记录退款信息")
    @PostMapping("/pay/refund")
    public void markRefunded(@RequestParam("orderId") Long orderId, @RequestParam("amount") BigDecimal amount) {
        Order order = orderMapper.selectById(orderId.intValue());
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            throw new BadRequestException("订单不存在");
        }
        if (order.getOrderStatus() == null
                || (order.getOrderStatus() != STATUS_UNDELIVERED
                && order.getOrderStatus() != STATUS_CONFIRMED)) {
            throw new BadRequestException("订单状态不允许退款");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus(STATUS_REFUND);
        order.setRefundAmount(amount);
        order.setRefundType("wxpay");
        order.setRefundTime(now);
        order.setUpdateTime(now);
        orderMapper.updateById(order);
    }
}

package com.litemall.controller;

import com.litemall.domain.DTO.OrderConfirmDTO;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.Result;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.OrderConfirmVO;
import com.litemall.domain.VO.OrderDetailVO;
import com.litemall.domain.VO.OrderListItemVO;
import com.litemall.domain.VO.OrderSubmitVO;
import com.litemall.service.ICartService;
import com.litemall.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/app/order")
@Tag(name = "order", description = "订单相关接口")
@RequiredArgsConstructor
public class OrderController {

    private final ICartService cartService;
    private final IOrderService orderService;

    @Operation(summary = "订单确认页试算")
    @PostMapping("/confirm")
    public Result<OrderConfirmVO> confirm(@Validated @RequestBody OrderConfirmDTO dto) {
        return Result.ok(cartService.confirmOrder(dto));
    }

    @Operation(summary = "申请下单幂等token")
    @PostMapping("/idem-token")
    public Result<Map<String, String>> idemToken() {
        return Result.ok(Map.of("idemToken", orderService.createIdemToken()));
    }

    @Operation(summary = "提交订单")
    @PostMapping("/submit")
    public Result<OrderSubmitVO> submit(@Validated @RequestBody OrderSubmitDTO dto) {
        return Result.ok(orderService.submitOrder(dto));
    }

    @Operation(summary = "我的订单列表")
    @GetMapping("/list")
    public Result<PageVO<OrderListItemVO>> list(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                @RequestParam(value = "status", required = false) String status) {
        return Result.ok(orderService.listOrder(pageNum, pageSize, status));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/detail")
    public Result<OrderDetailVO> detail(@RequestParam("orderId") Long orderId) {
        return Result.ok(orderService.getOrderDetail(orderId));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestParam("orderId") Long orderId) {
        orderService.cancelOrder(orderId);
        return Result.ok(null);
    }

    @Operation(summary = "确认收货")
    @PostMapping("/confirm-receive")
    public Result<Void> confirmReceive(@RequestParam("orderId") Long orderId) {
        orderService.confirmReceive(orderId);
        return Result.ok(null);
    }
}

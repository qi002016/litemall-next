package com.litemall.controller;

import com.litemall.domain.DTO.OrderConfirmDTO;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.Result;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.OrderConfirmVO;
import com.litemall.domain.VO.OrderDetailVO;
import com.litemall.domain.VO.OrderListItemVO;
import com.litemall.domain.VO.OrderSubmitVO;
import com.litemall.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>
 * C 端订单接口（CI-80 / CI-82 / CI-83 / CI-84 / CI-85 / CI-86 / CN-20）。
 * </p>
 * <p>
 * 契约（07 §2）：金额一律 int 分；{@code status} 出参是标识字符串，文案走 {@code statusText}；
 * 他人订单一律 404；可售不足只在 CI-80 行内提示、CI-82 整单 409。
 * </p>
 */
@RestController
@RequestMapping("/app/order")
@Tag(name = "order", description = "订单相关接口")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;

    @Operation(summary = "订单确认页试算（CI-80）")
    @PostMapping("/confirm")
    public Result<OrderConfirmVO> confirm(@Validated @RequestBody OrderConfirmDTO dto) {
        return Result.ok(orderService.confirmOrder(dto));
    }

    @Operation(summary = "申请下单幂等token（CI-81）")
    @PostMapping("/idem-token")
    public Result<Map<String, String>> idemToken() {
        return Result.ok(Map.of("idemToken", orderService.createIdemToken()));
    }

    @Operation(summary = "提交订单（CI-82）")
    @PostMapping("/submit")
    public Result<OrderSubmitVO> submit(@Validated @RequestBody OrderSubmitDTO dto) {
        return Result.ok(orderService.submitOrder(dto));
    }

    @Operation(summary = "我的订单列表（CI-83，7 栏）")
    @GetMapping("/list")
    public Result<PageVO<OrderListItemVO>> list(@RequestParam(value = "tab", required = false) String tab,
                                                @RequestParam(value = "status", required = false) String status,
                                                @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                                @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                @RequestParam(value = "sortBy", required = false) String sortBy,
                                                @RequestParam(value = "isAsc", required = false) Boolean isAsc) {
        return Result.ok(orderService.listOrder(tab, status, pageNo, pageSize, sortBy, isAsc));
    }

    @Operation(summary = "订单详情（CI-84）")
    @GetMapping("/detail")
    public Result<OrderDetailVO> detail(@RequestParam("orderId") Long orderId) {
        return Result.ok(orderService.getOrderDetail(orderId));
    }

    @Operation(summary = "取消订单（CI-85，未付款）")
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestParam("orderId") Long orderId) {
        orderService.cancelOrder(orderId);
        return Result.ok(null);
    }

    @Operation(summary = "确认收货（CI-86，已发货）")
    @PostMapping("/confirm-receive")
    public Result<Void> confirmReceive(@RequestParam("orderId") Long orderId) {
        orderService.confirmReceive(orderId);
        return Result.ok(null);
    }

    @Operation(summary = "删除订单（CN-20，C 端隐藏，幂等）")
    @PostMapping("/hide")
    public Result<Void> hide(@RequestParam("orderId") Long orderId) {
        orderService.hideOrder(orderId);
        return Result.ok(null);
    }
}
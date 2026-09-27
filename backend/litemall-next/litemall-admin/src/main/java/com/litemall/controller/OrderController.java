package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.AdminOrder;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.mapper.AdminOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class OrderController {

    private final AdminOrderMapper orderMapper;

    private String statusText(Integer s) {
        if (s == null) return "unknown";
        return switch (s) {
            case 101 -> "unpaid";
            case 102 -> "undelivered";
            case 201 -> "shipped";
            case 301 -> "confirmed";
            default -> (s >= 400 ? "refund" : "unknown");
        };
    }

    private Integer statusCode(String text) {
        if (!StringUtils.hasText(text)) return null;
        return switch (text) {
            case "unpaid" -> 101;
            case "undelivered" -> 102;
            case "shipped" -> 201;
            case "confirmed" -> 301;
            case "refund" -> 401;
            default -> null;
        };
    }

    @GetMapping("/list")
    public Result<PageVO<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        Integer code = statusCode(status);
        Page<AdminOrder> p = orderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminOrder>()
                        .eq(AdminOrder::getDeleted, false)
                        .eq(code != null, AdminOrder::getOrderStatus, code)
                        .orderByDesc(AdminOrder::getId));
        PageVO<Map<String, Object>> vo = PageVO.of(p, o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderSn", o.getOrderSn());
            m.put("userId", o.getUserId());
            m.put("orderAmount", o.getOrderPrice());
            m.put("status", statusText(o.getOrderStatus()));
            m.put("addTime", o.getAddTime());
            return m;
        });
        return Result.ok(vo);
    }

    @PostMapping("/ship")
    public Result<Void> ship(@RequestBody Map<String, Object> body) {
        Integer orderId = (Integer) body.get("orderId");
        AdminOrder o = new AdminOrder();
        o.setId(orderId);
        o.setOrderStatus(201); // 已发货
        o.setShipTime(LocalDateTime.now());
        orderMapper.updateById(o);
        return Result.ok();
    }

    @PostMapping("/refund")
    public Result<Void> refund(@RequestBody Map<String, Object> body) {
        Integer orderId = (Integer) body.get("orderId");
        AdminOrder o = new AdminOrder();
        o.setId(orderId);
        o.setOrderStatus(401); // 退款
        o.setRefundTime(LocalDateTime.now());
        orderMapper.updateById(o);
        return Result.ok();
    }
}

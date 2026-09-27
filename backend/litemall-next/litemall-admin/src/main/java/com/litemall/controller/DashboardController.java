package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litemall.domain.Result;
import com.litemall.mapper.AdminGoodsMapper;
import com.litemall.mapper.AdminOrderMapper;
import com.litemall.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AdminGoodsMapper goodsMapper;
    private final AdminOrderMapper orderMapper;
    private final AdminUserMapper userMapper;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long goodsTotal = goodsMapper.selectCount(new LambdaQueryWrapper<com.litemall.domain.AdminGoods>()
                .eq(com.litemall.domain.AdminGoods::getDeleted, false));
        long userTotal = userMapper.selectCount(new LambdaQueryWrapper<com.litemall.domain.AdminUser>()
                .eq(com.litemall.domain.AdminUser::getDeleted, false));
        LocalDateTime today = LocalDate.now().atStartOfDay();
        long todayOrders = orderMapper.selectCount(new LambdaQueryWrapper<com.litemall.domain.AdminOrder>()
                .eq(com.litemall.domain.AdminOrder::getDeleted, false)
                .ge(com.litemall.domain.AdminOrder::getAddTime, today));
        long undelivered = orderMapper.selectCount(new LambdaQueryWrapper<com.litemall.domain.AdminOrder>()
                .eq(com.litemall.domain.AdminOrder::getDeleted, false)
                .eq(com.litemall.domain.AdminOrder::getOrderStatus, 102));
        Map<String, Object> m = new HashMap<>();
        m.put("goodsTotal", goodsTotal);
        m.put("userTotal", userTotal);
        m.put("todayOrders", todayOrders);
        m.put("undelivered", undelivered);
        return Result.ok(m);
    }
}

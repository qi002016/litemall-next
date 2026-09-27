package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.*;
import com.litemall.mapper.AdminAdMapper;
import com.litemall.mapper.AdminCouponMapper;
import com.litemall.mapper.AdminTopicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/admin/market")
@RequiredArgsConstructor
public class MarketingController {

    private final AdminCouponMapper couponMapper;
    private final AdminAdMapper adMapper;
    private final AdminTopicMapper topicMapper;

    @GetMapping("/coupon/list")
    public Result<PageVO<AdminCoupon>> couponList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name) {
        Page<AdminCoupon> p = couponMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminCoupon>()
                        .eq(AdminCoupon::getDeleted, false)
                        .like(StringUtils.hasText(name), AdminCoupon::getName, name)
                        .orderByDesc(AdminCoupon::getId));
        return Result.ok(PageVO.of(p));
    }

    @GetMapping("/ad/list")
    public Result<PageVO<AdminAd>> adList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name) {
        Page<AdminAd> p = adMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminAd>()
                        .eq(AdminAd::getDeleted, false)
                        .like(StringUtils.hasText(name), AdminAd::getName, name)
                        .orderByDesc(AdminAd::getId));
        return Result.ok(PageVO.of(p));
    }

    @GetMapping("/topic/list")
    public Result<PageVO<AdminTopic>> topicList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title) {
        Page<AdminTopic> p = topicMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminTopic>()
                        .eq(AdminTopic::getDeleted, false)
                        .like(StringUtils.hasText(title), AdminTopic::getTitle, title)
                        .orderByDesc(AdminTopic::getId));
        return Result.ok(PageVO.of(p));
    }

    // ---------- 优惠券 CRUD ----------
    @PostMapping("/coupon/create")
    public Result<Void> couponCreate(@RequestBody AdminCoupon c) {
        c.setId(null);
        c.setStatus(0);
        c.setDeleted(Boolean.FALSE);
        c.setAddTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());
        couponMapper.insert(c);
        return Result.ok();
    }

    @PutMapping("/coupon/update")
    public Result<Void> couponUpdate(@RequestBody AdminCoupon c) {
        c.setUpdateTime(LocalDateTime.now());
        couponMapper.updateById(c);
        return Result.ok();
    }

    @DeleteMapping("/coupon/delete")
    public Result<Void> couponDelete(@RequestParam Integer id) {
        AdminCoupon c = new AdminCoupon();
        c.setId(id);
        c.setDeleted(Boolean.TRUE);
        c.setUpdateTime(LocalDateTime.now());
        couponMapper.updateById(c);
        return Result.ok();
    }

    // ---------- 广告 CRUD ----------
    @PostMapping("/ad/create")
    public Result<Void> adCreate(@RequestBody AdminAd a) {
        a.setId(null);
        a.setDeleted(Boolean.FALSE);
        a.setAddTime(LocalDateTime.now());
        a.setUpdateTime(LocalDateTime.now());
        adMapper.insert(a);
        return Result.ok();
    }

    @PutMapping("/ad/update")
    public Result<Void> adUpdate(@RequestBody AdminAd a) {
        a.setUpdateTime(LocalDateTime.now());
        adMapper.updateById(a);
        return Result.ok();
    }

    @DeleteMapping("/ad/delete")
    public Result<Void> adDelete(@RequestParam Integer id) {
        AdminAd a = new AdminAd();
        a.setId(id);
        a.setDeleted(Boolean.TRUE);
        a.setUpdateTime(LocalDateTime.now());
        adMapper.updateById(a);
        return Result.ok();
    }

    // ---------- 专题 CRUD ----------
    @PostMapping("/topic/create")
    public Result<Void> topicCreate(@RequestBody AdminTopic t) {
        t.setId(null);
        t.setDeleted(Boolean.FALSE);
        t.setAddTime(LocalDateTime.now());
        t.setUpdateTime(LocalDateTime.now());
        topicMapper.insert(t);
        return Result.ok();
    }

    @PutMapping("/topic/update")
    public Result<Void> topicUpdate(@RequestBody AdminTopic t) {
        t.setUpdateTime(LocalDateTime.now());
        topicMapper.updateById(t);
        return Result.ok();
    }

    @DeleteMapping("/topic/delete")
    public Result<Void> topicDelete(@RequestParam Integer id) {
        AdminTopic t = new AdminTopic();
        t.setId(id);
        t.setDeleted(Boolean.TRUE);
        t.setUpdateTime(LocalDateTime.now());
        topicMapper.updateById(t);
        return Result.ok();
    }
}

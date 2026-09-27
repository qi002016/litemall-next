package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.AdminGoods;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.mapper.AdminGoodsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final AdminGoodsMapper goodsMapper;

    @GetMapping("/list")
    public Result<PageVO<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name) {
        Page<AdminGoods> p = goodsMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminGoods>()
                        .eq(AdminGoods::getDeleted, false)
                        .like(StringUtils.hasText(name), AdminGoods::getName, name)
                        .orderByDesc(AdminGoods::getId));
        PageVO<Map<String, Object>> vo = PageVO.of(p, g -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.getId());
            m.put("name", g.getName());
            m.put("brief", g.getBrief());
            m.put("minPrice", g.getRetailPrice());
            m.put("onSale", g.getIsOnSale());
            return m;
        });
        return Result.ok(vo);
    }

    @PostMapping("/create")
    public Result<Void> create(@RequestBody Map<String, Object> body) {
        AdminGoods g = new AdminGoods();
        g.setName((String) body.get("name"));
        g.setBrief((String) body.get("brief"));
        g.setPicUrl((String) body.get("picUrl"));
        g.setRetailPrice(toBD(body.get("minPrice")));
        g.setIsOnSale(Boolean.TRUE);
        g.setDeleted(Boolean.FALSE);
        g.setAddTime(LocalDateTime.now());
        g.setUpdateTime(LocalDateTime.now());
        goodsMapper.insert(g);
        return Result.ok();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody Map<String, Object> body) {
        Integer id = (Integer) body.get("id");
        AdminGoods g = new AdminGoods();
        g.setId(id);
        g.setName((String) body.get("name"));
        g.setBrief((String) body.get("brief"));
        if (body.get("picUrl") != null) g.setPicUrl((String) body.get("picUrl"));
        if (body.get("minPrice") != null) g.setRetailPrice(toBD(body.get("minPrice")));
        if (body.get("onSale") != null) g.setIsOnSale((Boolean) body.get("onSale"));
        g.setUpdateTime(LocalDateTime.now());
        goodsMapper.updateById(g);
        return Result.ok();
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam Integer id) {
        AdminGoods g = new AdminGoods();
        g.setId(id);
        g.setDeleted(Boolean.TRUE);
        g.setUpdateTime(LocalDateTime.now());
        goodsMapper.updateById(g);
        return Result.ok();
    }

    private java.math.BigDecimal toBD(Object v) {
        if (v == null) return null;
        return new java.math.BigDecimal(v.toString());
    }
}

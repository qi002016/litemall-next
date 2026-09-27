package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.domain.DTO.GoodsCreateDTO;
import com.litemall.domain.DTO.GoodsUpdateDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.GoodsAdminDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 管理端商品接口。
 * </p>
 * <p>
 * H5：不再持有 {@code AdminGoodsMapper}，所有商品读写经 {@link GoodsClient}
 * 调用 goods 服务的 {@code /inner/goods/admin/**}（含商品详情缓存失效）。
 * 对外路径/方法/字段与改造前一致。
 * </p>
 */
@RestController
@RequestMapping("/admin/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsClient goodsClient;

    @GetMapping("/list")
    public Result<PageVO<GoodsAdminDTO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name) {
        return Result.ok(goodsClient.adminListGoods(pageNum, pageSize, name));
    }

    @PostMapping("/create")
    public Result<Void> create(@Valid @RequestBody GoodsCreateDTO body) {
        GoodsAdminDTO dto = new GoodsAdminDTO();
        dto.setName(body.getName());
        dto.setBrief(body.getBrief());
        dto.setPicUrl(body.getPicUrl());
        dto.setMinPrice(body.getMinPrice());
        goodsClient.adminCreateGoods(dto);
        return Result.ok();
    }

    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody GoodsUpdateDTO body) {
        GoodsAdminDTO dto = new GoodsAdminDTO();
        dto.setId(body.getId());
        dto.setName(body.getName());
        dto.setBrief(body.getBrief());
        dto.setPicUrl(body.getPicUrl());
        dto.setMinPrice(body.getMinPrice());
        dto.setOnSale(body.getOnSale());
        goodsClient.adminUpdateGoods(dto);
        return Result.ok();
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        goodsClient.adminDeleteGoods(id);
        return Result.ok();
    }
}
package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.GoodsAdminDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

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
    public Result<Void> create(@RequestBody Map<String, Object> body) {
        GoodsAdminDTO dto = new GoodsAdminDTO();
        dto.setName(asString(body.get("name")));
        dto.setBrief(asString(body.get("brief")));
        dto.setPicUrl(asString(body.get("picUrl")));
        dto.setMinPrice(toBD(body.get("minPrice")));
        goodsClient.adminCreateGoods(dto);
        return Result.ok();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody Map<String, Object> body) {
        GoodsAdminDTO dto = new GoodsAdminDTO();
        dto.setId(asInteger(body.get("id")));
        dto.setName(asString(body.get("name")));
        dto.setBrief(asString(body.get("brief")));
        dto.setPicUrl(asString(body.get("picUrl")));
        dto.setMinPrice(toBD(body.get("minPrice")));
        dto.setOnSale(asBoolean(body.get("onSale")));
        goodsClient.adminUpdateGoods(dto);
        return Result.ok();
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam Integer id) {
        goodsClient.adminDeleteGoods(id);
        return Result.ok();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(value.toString());
    }

    private BigDecimal toBD(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return new BigDecimal(value.toString());
    }
}
package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.GoodsMapper;
import com.litemall.mapper.GoodsProductMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品服务内部接口（供其他服务 Feign 调用, 不走网关鉴权）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
@RestController
@RequestMapping("/inner/goods")
@RequiredArgsConstructor
@Tag(name = "商品内部接口", description = "供服务间 Feign 调用的商品接口")
public class GoodsInnerController {

    private final GoodsProductMapper goodsProductMapper;
    private final GoodsMapper goodsMapper;

    @Operation(summary = "根据货品ID查询商品货品信息")
    @GetMapping("/product/info")
    public GoodsProductInfoDTO getProductInfo(@RequestParam("productId") Long productId) {
        GoodsProduct product = goodsProductMapper.selectById(productId);
        if (product == null || Boolean.TRUE.equals(product.getDeleted())) {
            return null;
        }
        return toDTO(product);
    }

    @Operation(summary = "根据商品ID查询默认货品（无规格商品加购用）")
    @GetMapping("/product/default")
    public GoodsProductInfoDTO getDefaultProduct(@RequestParam("goodsId") Long goodsId) {
        List<GoodsProduct> products = goodsProductMapper.selectList(new LambdaQueryWrapper<GoodsProduct>()
                .eq(GoodsProduct::getGoodsId, goodsId.intValue())
                .eq(GoodsProduct::getDeleted, false)
                .orderByAsc(GoodsProduct::getId));
        if (products.isEmpty()) {
            return null;
        }
        return toDTO(products.get(0));
    }

    /** 货品 + 商品信息组装 */
    private GoodsProductInfoDTO toDTO(GoodsProduct product) {
        Goods goods = goodsMapper.selectById(product.getGoodsId());

        GoodsProductInfoDTO dto = new GoodsProductInfoDTO();
        dto.setId(product.getId().longValue());
        dto.setGoodsId(product.getGoodsId().longValue());
        dto.setPrice(product.getPrice());
        dto.setSpecifications(product.getSpecifications());
        dto.setUrl(product.getUrl());
        if (goods != null) {
            dto.setGoodsName(goods.getName());
            dto.setGoodsSn(goods.getGoodsSn());
            dto.setPicUrl(goods.getPicUrl());
        }
        return dto;
    }

    @Operation(summary = "扣减货品库存（下单场景）")
    @PostMapping("/product/deduct")
    public void deductStock(@RequestBody Map<String, Integer> param) {
        Integer productId = param.get("productId");
        Integer number = param.get("number");
        if (productId == null || number == null || number <= 0) {
            throw new BadRequestException("扣库存参数错误");
        }
        int rows = goodsProductMapper.update(null, new LambdaUpdateWrapper<GoodsProduct>()
                .eq(GoodsProduct::getId, productId)
                .eq(GoodsProduct::getDeleted, false)
                .ge(GoodsProduct::getNumber, number)
                .setSql("number = number - {0}", number));
        if (rows == 0) {
            throw new BadRequestException("库存不足");
        }
    }

    @Operation(summary = "回补货品库存（取消订单场景）")
    @PostMapping("/product/release")
    public void releaseStock(@RequestBody Map<String, Integer> param) {
        Integer productId = param.get("productId");
        Integer number = param.get("number");
        if (productId == null || number == null || number <= 0) {
            throw new BadRequestException("回补库存参数错误");
        }
        goodsProductMapper.update(null, new LambdaUpdateWrapper<GoodsProduct>()
                .eq(GoodsProduct::getId, productId)
                .eq(GoodsProduct::getDeleted, false)
                .setSql("number = number + {0}", number));
    }

    @Operation(summary = "根据商品ID批量查询商品简要信息")
    @GetMapping("/brief/list")
    public List<GoodsBriefDTO> listGoodsBriefByIds(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> idList = ids.stream().map(Long::intValue).collect(Collectors.toList());
        List<Goods> goodsList = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .in(Goods::getId, idList)
                .eq(Goods::getDeleted, false));
        return goodsList.stream().map(g -> {
            GoodsBriefDTO dto = new GoodsBriefDTO();
            dto.setId(g.getId().longValue());
            dto.setName(g.getName());
            dto.setPicUrl(g.getPicUrl());
            return dto;
        }).collect(Collectors.toList());
    }
}

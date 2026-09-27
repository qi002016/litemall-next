package com.litemall.controller;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litemall.cache.CacheService;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.GoodsMapper;
import com.litemall.mapper.GoodsProductMapper;
import com.litemall.service.IGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final CacheService cacheService;
    private final IGoodsService goodsService;

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
                .eq(GoodsProduct::getGoodsId, goodsId)
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
        dto.setId(product.getId());
        dto.setGoodsId(product.getGoodsId());
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
    public void deductStock(@RequestBody Map<String, Object> param) {
        // M10：请求体容器放宽为 Object，货品ID 按 64 位解析（原 Map<String,Integer> 会在 id>2^31 时反序列化失败）；
        // 数量仍为 Integer（number 属数量语义，不改类型），JSON 线上格式不变
        Long productId = Convert.toLong(param.get("productId"));
        Integer number = Convert.toInt(param.get("number"));
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
        // H6：库存变化后精确失效该商品的实时库存 key（Redis 级删除，所有节点立即回源）
        evictStock(productId);
    }

    @Operation(summary = "回补货品库存（取消订单场景）")
    @PostMapping("/product/release")
    public void releaseStock(@RequestBody Map<String, Object> param) {
        // M10：同上，货品ID 按 64 位解析，数量保持 Integer
        Long productId = Convert.toLong(param.get("productId"));
        Integer number = Convert.toInt(param.get("number"));
        if (productId == null || number == null || number <= 0) {
            throw new BadRequestException("回补库存参数错误");
        }
        goodsProductMapper.update(null, new LambdaUpdateWrapper<GoodsProduct>()
                .eq(GoodsProduct::getId, productId)
                .eq(GoodsProduct::getDeleted, false)
                .setSql("number = number + {0}", number));
        // H6：回补后同样精确失效实时库存 key
        evictStock(productId);
    }

    @Operation(summary = "批量扣减货品库存（下单场景，M2：一次 RPC 完成）")
    @PostMapping("/product/deduct/batch")
    @Transactional(rollbackFor = Exception.class)
    public void deductStockBatch(@RequestBody StockChangeDTO dto) {
        List<StockChangeDTO.Item> items = dto == null ? null : dto.getItems();
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("扣库存参数错误");
        }
        for (StockChangeDTO.Item item : items) {
            Long productId = validateItem(item, "扣库存参数错误");
            int rows = goodsProductMapper.update(null, new LambdaUpdateWrapper<GoodsProduct>()
                    .eq(GoodsProduct::getId, productId)
                    .eq(GoodsProduct::getDeleted, false)
                    .ge(GoodsProduct::getNumber, item.getNumber())
                    .setSql("number = number - {0}", item.getNumber()));
            if (rows == 0) {
                // 任一货品库存不足：本地事务回滚，保证不会出现"部分扣减成功"
                throw new BadRequestException("库存不足");
            }
            evictStock(productId);
        }
    }

    @Operation(summary = "批量回补货品库存（取消订单场景，M2：一次 RPC 完成）")
    @PostMapping("/product/release/batch")
    @Transactional(rollbackFor = Exception.class)
    public void releaseStockBatch(@RequestBody StockChangeDTO dto) {
        List<StockChangeDTO.Item> items = dto == null ? null : dto.getItems();
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("回补库存参数错误");
        }
        for (StockChangeDTO.Item item : items) {
            Long productId = validateItem(item, "回补库存参数错误");
            goodsProductMapper.update(null, new LambdaUpdateWrapper<GoodsProduct>()
                    .eq(GoodsProduct::getId, productId)
                    .eq(GoodsProduct::getDeleted, false)
                    .setSql("number = number + {0}", item.getNumber()));
            evictStock(productId);
        }
    }

    /** M2：批量变更项参数校验，返回可用于 SQL 的 productId */
    private Long validateItem(StockChangeDTO.Item item, String errorMsg) {
        if (item == null || item.getProductId() == null || item.getNumber() == null || item.getNumber() <= 0) {
            throw new BadRequestException(errorMsg);
        }
        return item.getProductId();
    }

    /**
     * H6：按货品ID找到商品ID并删除其实时库存缓存。
     * 删除失败不阻断主流程（库存本身已落库，最坏情况是缓存多存活一个短 TTL）。
     */
    private void evictStock(Long productId) {
        GoodsProduct product = goodsProductMapper.selectById(productId);
        if (product == null || product.getGoodsId() == null) {
            return;
        }
        cacheService.del("goods:stock:" + product.getGoodsId());
    }

    @Operation(summary = "根据商品ID批量查询商品简要信息")
    @GetMapping("/brief/list")
    public List<GoodsBriefDTO> listGoodsBriefByIds(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Goods> goodsList = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .in(Goods::getId, ids)
                .eq(Goods::getDeleted, false));
        return goodsList.stream().map(g -> {
            GoodsBriefDTO dto = new GoodsBriefDTO();
            dto.setId(g.getId());
            dto.setName(g.getName());
            dto.setPicUrl(g.getPicUrl());
            return dto;
        }).collect(Collectors.toList());
    }

    /* ==================== H5：管理端商品内部接口（admin 服务 Feign 调用） ==================== */

    @Operation(summary = "管理端商品分页列表")
    @GetMapping("/admin/list")
    public PageVO<GoodsAdminDTO> adminList(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                           @RequestParam(value = "name", required = false) String name) {
        return goodsService.adminListGoods(pageNum, pageSize, name);
    }

    @Operation(summary = "管理端商品总数")
    @GetMapping("/admin/count")
    public Long adminCount() {
        return goodsService.adminCountGoods();
    }

    @Operation(summary = "管理端新建商品")
    @PostMapping("/admin/create")
    public Long adminCreate(@RequestBody GoodsAdminDTO dto) {
        return goodsService.adminCreateGoods(dto);
    }

    @Operation(summary = "管理端更新商品")
    @PutMapping("/admin/update")
    public void adminUpdate(@RequestBody GoodsAdminDTO dto) {
        goodsService.adminUpdateGoods(dto);
    }

    @Operation(summary = "管理端删除商品")
    @DeleteMapping("/admin/delete")
    public void adminDelete(@RequestParam("id") Long id) {
        goodsService.adminDeleteGoods(id);
    }
}

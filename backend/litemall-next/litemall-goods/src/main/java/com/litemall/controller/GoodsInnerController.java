package com.litemall.controller;

import cn.hutool.core.convert.Convert;
import com.litemall.domain.DTO.GoodsAdminQuery;
import com.litemall.domain.DTO.StockAdminQuery;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.DeductBatchResultDTO;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.domain.dto.ReleasedBatchResultDTO;
import com.litemall.domain.dto.StockAdminDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.GoodsApiException;
import com.litemall.exception.InsufficientStockException;
import com.litemall.service.IGoodsProductService;
import com.litemall.service.IGoodsService;
import com.litemall.service.IGoodsWriteService;
import com.litemall.service.IStockService;
import com.litemall.support.GoodsSupport;
import com.litemall.support.LockedStockProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
 * <p>
 * R1 口径：{@code /inner/**} <b>保留裸类型 + 非 200 HTTP</b>（不套 {@code Result}、不加 {@code data.errorCode}），
 * 唯一例外是 CI-44 批量扣减——按 03 §4.7 D-24 用 HTTP 200 + {@code DeductBatchResultDTO}
 * 表达「库存不足」，让 order 侧能拿到 {@code shortage} 明细而不是靠异常码猜。
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

    private final IGoodsProductService goodsProductService;
    private final IGoodsService goodsService;
    private final IGoodsWriteService goodsWriteService;
    private final IStockService stockService;
    private final LockedStockProvider lockedStockProvider;

    /* ==================== 货品信息（CI-40） ==================== */

    @Operation(summary = "根据货品ID查询商品货品信息")
    @GetMapping("/product/info")
    public GoodsProductInfoDTO getProductInfo(@RequestParam("productId") Long productId) {
        GoodsProduct product = goodsProductService.getById(productId);
        if (product == null || Boolean.TRUE.equals(product.getDeleted())) {
            return null;
        }
        return toDTO(product);
    }

    @Operation(summary = "根据商品ID查询默认货品（无规格商品加购用）")
    @GetMapping("/product/default")
    public GoodsProductInfoDTO getDefaultProduct(@RequestParam("goodsId") Long goodsId) {
        List<GoodsProduct> products = goodsProductService.lambdaQuery()
                .eq(GoodsProduct::getGoodsId, goodsId)
                .eq(GoodsProduct::getDeleted, false)
                .orderByAsc(GoodsProduct::getId)
                .list();
        if (products.isEmpty()) {
            return null;
        }
        return toDTO(products.get(0));
    }

    /**
     * 货品 + 商品信息组装。
     *
     * <p>
     * {@code sellable} 需要 order 域派生的 locked；order 不可达时<b>降级为 number</b>
     * （08 §2.1 的「不得当 0」针对的是展示侧；这里是下单校验路径，宁可少拦也不能让下单整体不可用，
     * 真正的超卖保护在 CI-42 的条件扣减）。
     * </p>
     */
    private GoodsProductInfoDTO toDTO(GoodsProduct product) {
        Goods goods = goodsService.getById(product.getGoodsId());

        GoodsProductInfoDTO dto = new GoodsProductInfoDTO();
        dto.setId(product.getId());
        dto.setGoodsId(product.getGoodsId());
        dto.setPrice(product.getPrice());
        dto.setSpecifications(product.getSpecifications());
        dto.setUrl(product.getUrl());
        dto.setSkuCode(product.getSkuCode());
        dto.setSpecValueIds(GoodsSupport.parseSpecValueIds(product.getSpecValueIds()));
        dto.setNumber(product.getNumber());
        LockedStockProvider.LockedStockQuery locked = lockedStockProvider.query(List.of(product.getId()));
        Integer sellable = GoodsSupport.sellable(product.getNumber(), locked.lockedOf(product.getId()),
                !locked.available());
        dto.setSellable(sellable == null ? product.getNumber() : sellable);
        if (goods != null) {
            dto.setGoodsName(goods.getName());
            dto.setGoodsSn(goods.getGoodsSn());
            dto.setPicUrl(goods.getPicUrl());
        }
        return dto;
    }

    /* ==================== 库存扣减 / 回补（CI-42/43/44/45） ==================== */

    @Operation(summary = "扣减货品库存（下单场景）")
    @PostMapping("/product/deduct")
    public void deductStock(@RequestBody Map<String, Object> param) {
        Long productId = Convert.toLong(param.get("productId"));
        Integer number = Convert.toInt(param.get("number"));
        // orderId 仅用于调用链审计，商品域不据此做幂等（幂等在 order 侧）
        stockService.deduct(productId, number);
    }

    @Operation(summary = "回补货品库存（售后退款场景）")
    @PostMapping("/product/release")
    public void releaseStock(@RequestBody Map<String, Object> param) {
        Long productId = Convert.toLong(param.get("productId"));
        Integer number = Convert.toInt(param.get("number"));
        stockService.release(productId, number);
    }

    @Operation(summary = "批量扣减货品库存（下单场景，M2：一次 RPC 完成）")
    @PostMapping("/product/deduct/batch")
    public DeductBatchResultDTO deductStockBatch(@RequestBody StockChangeDTO dto) {
        try {
            return stockService.deductBatch(dto);
        } catch (InsufficientStockException e) {
            // 事务已在 service 边界回滚；这里把缺口明细回给 order，由它保持订单未付款
            return DeductBatchResultDTO.failure(e.getItems());
        }
    }

    @Operation(summary = "批量回补货品库存（售后退款场景，M2：一次 RPC 完成）")
    @PostMapping("/product/release/batch")
    public ReleasedBatchResultDTO releaseStockBatch(@RequestBody StockChangeDTO dto) {
        return stockService.releaseBatch(dto);
    }

    @Operation(summary = "根据商品ID批量查询商品简要信息")
    @GetMapping("/brief/list")
    public List<GoodsBriefDTO> listGoodsBriefByIds(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Goods> goodsList = goodsService.listByIds(ids).stream()
                .filter(g -> !Boolean.TRUE.equals(g.getDeleted()))
                .toList();
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
    public PageVO<GoodsAdminDTO> adminList(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "onSale", required = false) Boolean onSale,
            @RequestParam(value = "sortBy", required = false) String sortBy) {
        GoodsAdminQuery query = new GoodsAdminQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        // D-18：入参名由 name 改为 keyword，旧调用名保留兼容
        query.setKeyword(keyword != null ? keyword : name);
        query.setCategoryId(categoryId);
        query.setBrandId(brandId);
        query.setStatus(status);
        query.setOnSale(onSale);
        query.setSortBy(sortBy);
        return goodsService.adminListGoods(query);
    }

    @Operation(summary = "管理端商品总数")
    @GetMapping("/admin/count")
    public Long adminCount() {
        return goodsService.adminCountGoods();
    }

    @Operation(summary = "管理端新建商品")
    @PostMapping("/admin/create")
    public Long adminCreate(@RequestBody GoodsAdminDTO dto) {
        return goodsWriteService.adminCreateGoods(dto);
    }

    @Operation(summary = "管理端更新商品")
    @PutMapping("/admin/update")
    public void adminUpdate(@RequestBody GoodsAdminDTO dto) {
        goodsWriteService.adminUpdateGoods(dto);
    }

    @Operation(summary = "管理端删除商品")
    @DeleteMapping("/admin/delete")
    public void adminDelete(@RequestParam("id") Long id) {
        goodsWriteService.adminDeleteGoods(id);
    }

    /* ==================== 管理端库存（CN-13 / CN-14） ==================== */

    @Operation(summary = "管理端库存分页列表")
    @GetMapping("/admin/stock/list")
    public PageVO<StockAdminDTO> adminStockList(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "goodsStatus", required = false) String goodsStatus,
            @RequestParam(value = "lowStockOnly", required = false) Boolean lowStockOnly,
            @RequestParam(value = "soldOutOnly", required = false) Boolean soldOutOnly,
            @RequestParam(value = "sortBy", required = false) String sortBy) {
        StockAdminQuery query = new StockAdminQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        query.setCategoryId(categoryId);
        query.setGoodsStatus(goodsStatus);
        query.setLowStockOnly(lowStockOnly);
        query.setSoldOutOnly(soldOutOnly);
        query.setSortBy(sortBy);
        return stockService.adminStockList(query);
    }

    @Operation(summary = "管理端库存调整（绝对值 + CAS）")
    @PutMapping("/admin/stock/update")
    public StockAdminDTO adminStockUpdate(@RequestBody StockAdminDTO dto) {
        return stockService.adminStockUpdate(dto);
    }

    }
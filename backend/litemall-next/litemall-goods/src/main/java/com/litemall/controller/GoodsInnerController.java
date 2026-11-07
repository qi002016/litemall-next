package com.litemall.controller;

import cn.hutool.core.convert.Convert;
import com.litemall.domain.DTO.BrandAdminQuery;
import com.litemall.domain.DTO.CommentAdminQuery;
import com.litemall.domain.DTO.GoodsAdminQuery;
import com.litemall.domain.DTO.StockAdminQuery;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.BrandAdminDTO;
import com.litemall.domain.dto.BrandAdminVO;
import com.litemall.domain.dto.CategoryAdminDTO;
import com.litemall.domain.dto.CategoryAdminVO;
import com.litemall.domain.dto.CategoryCreateResultDTO;
import com.litemall.domain.dto.CommentAdminVO;
import com.litemall.domain.dto.CommentReplyDTO;
import com.litemall.domain.dto.CommentReplyResultDTO;
import com.litemall.domain.dto.CommentStatusDTO;
import com.litemall.domain.dto.CommentStatusResultDTO;
import com.litemall.domain.dto.DeductBatchResultDTO;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.domain.dto.ReleasedBatchResultDTO;
import com.litemall.domain.dto.StockAdminDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.GoodsApiException;
import com.litemall.exception.InsufficientStockException;
import com.litemall.service.IBrandService;
import com.litemall.service.ICategoryService;
import com.litemall.service.ICommentService;
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
    private final ICategoryService categoryService;
    private final IBrandService brandService;
    private final ICommentService commentService;

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
        // 延续原有 deleted 过滤：被逻辑删除的商品不该出现在收藏/足迹列表里
        List<Goods> goodsList = goodsService.listByIds(ids).stream()
                .filter(g -> !Boolean.TRUE.equals(g.getDeleted()))
                .toList();
        if (goodsList.isEmpty()) {
            return new ArrayList<>();
        }

        // 一次 in 批量取 SKU 再按 goodsId 分组，避免每个商品各查一次形成 N+1
        List<Long> validIds = goodsList.stream().map(Goods::getId).toList();
        Map<Long, List<GoodsProduct>> skuMap = goodsProductService.lambdaQuery()
                .in(GoodsProduct::getGoodsId, validIds)
                .eq(GoodsProduct::getDeleted, false)
                .list()
                .stream()
                .collect(Collectors.groupingBy(GoodsProduct::getGoodsId));

        return goodsList.stream().map(g -> {
            GoodsBriefDTO dto = new GoodsBriefDTO();
            dto.setId(g.getId());
            dto.setName(g.getName());
            dto.setPicUrl(g.getPicUrl());
            // 有未删除 SKU 取最低价；无 SKU（或价格全缺）回落零售价，统一按元→分转换；都没有则留 null，不造 0
            Integer minFen = null;
            List<GoodsProduct> skus = skuMap.get(g.getId());
            if (skus != null) {
                for (GoodsProduct sku : skus) {
                    Integer fen = GoodsSupport.yuanToFen(sku.getPrice());
                    if (fen != null && (minFen == null || fen < minFen)) {
                        minFen = fen;
                    }
                }
            }
            if (minFen == null) {
                minFen = GoodsSupport.yuanToFen(g.getRetailPrice());
            }
            dto.setPrice(minFen);
            // 上面已过滤 deleted，这里仍显式带上 deleted 条件，防止过滤口径日后被改时 onSale 误判
            dto.setOnSale(Boolean.TRUE.equals(g.getIsOnSale()) && !Boolean.TRUE.equals(g.getDeleted()));
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

    /* ==================== 管理端分类 / 品牌（CN-21~28 对内，05 §4.1） ==================== */

    @Operation(summary = "管理端分类树")
    @GetMapping("/admin/category/list")
    public List<CategoryAdminVO> adminCategoryList(
            @RequestParam(value = "withGoodsCount", required = false) Boolean withGoodsCount) {
        return categoryService.adminTree(Boolean.TRUE.equals(withGoodsCount));
    }

    @Operation(summary = "管理端新建分类")
    @PostMapping("/admin/category/create")
    public CategoryCreateResultDTO adminCategoryCreate(@RequestBody CategoryAdminDTO dto) {
        return categoryService.createCategory(dto);
    }

    @Operation(summary = "管理端修改分类")
    @PutMapping("/admin/category/update")
    public void adminCategoryUpdate(@RequestBody CategoryAdminDTO dto) {
        categoryService.updateCategory(dto);
    }

    @Operation(summary = "管理端删除分类（物理删除）")
    @DeleteMapping("/admin/category/delete")
    public void adminCategoryDelete(@RequestParam("id") Long id) {
        categoryService.deleteCategory(id);
    }

    @Operation(summary = "管理端品牌分页列表")
    @GetMapping("/admin/brand/list")
    public PageVO<BrandAdminVO> adminBrandList(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "isAsc", required = false) Boolean isAsc) {
        BrandAdminQuery query = new BrandAdminQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        query.setSortBy(sortBy);
        query.setIsAsc(isAsc);
        return brandService.adminListBrands(query);
    }

    @Operation(summary = "管理端新建品牌")
    @PostMapping("/admin/brand/create")
    public Long adminBrandCreate(@RequestBody BrandAdminDTO dto) {
        return brandService.createBrand(dto);
    }

    @Operation(summary = "管理端修改品牌")
    @PutMapping("/admin/brand/update")
    public void adminBrandUpdate(@RequestBody BrandAdminDTO dto) {
        brandService.updateBrand(dto);
    }

    @Operation(summary = "管理端删除品牌（物理删除）")
    @DeleteMapping("/admin/brand/delete")
    public void adminBrandDelete(@RequestParam("id") Long id) {
        brandService.deleteBrand(id);
    }

    /* ==================== 管理端评价（CN-29~31 对内，05 §3.9~§3.11） ==================== */

    @Operation(summary = "管理端评价分页列表（含已隐藏）")
    @GetMapping("/admin/comment/list")
    public PageVO<CommentAdminVO> adminCommentList(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "goodsId", required = false) Long goodsId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "star", required = false) String star,
            @RequestParam(value = "replied", required = false) Boolean replied,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo) {
        CommentAdminQuery query = new CommentAdminQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setGoodsId(goodsId);
        query.setKeyword(keyword);
        query.setStar(star);
        query.setReplied(replied);
        query.setStatus(status);
        query.setDateFrom(dateFrom);
        query.setDateTo(dateTo);
        return commentService.adminListComments(query);
    }

    @Operation(summary = "管理端回复评价")
    @PostMapping("/admin/comment/reply")
    public CommentReplyResultDTO adminCommentReply(@RequestBody CommentReplyDTO dto) {
        return commentService.replyComment(dto);
    }

    @Operation(summary = "管理端隐藏/恢复评价")
    @PostMapping("/admin/comment/status")
    public CommentStatusResultDTO adminCommentStatus(@RequestBody CommentStatusDTO dto) {
        return commentService.updateCommentStatus(dto);
    }
}
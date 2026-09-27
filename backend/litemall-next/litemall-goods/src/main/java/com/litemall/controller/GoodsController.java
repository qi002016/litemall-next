package com.litemall.controller;


import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.BrandVO;
import com.litemall.domain.VO.CategoryVO;
import com.litemall.domain.VO.GoodsDetailVO;
import com.litemall.domain.VO.GoodsVO;
import com.litemall.service.IBrandService;
import com.litemall.service.ICategoryService;
import com.litemall.service.IGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;



/**
 * <p>
 * 商品基本信息表 前端控制器
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@RestController
@RequestMapping("/app/goods")
@RequiredArgsConstructor
@Tag(name = "商品", description = "商品相关接口")
public class GoodsController {

    private final IGoodsService goodsService;
    private final ICategoryService categoryService;
    private final IBrandService brandService;

    @Operation(summary = "分页获取商品列表")
    @GetMapping("/list")
    public Result<PageVO<GoodsVO>> getGoodsList(int pageNum, int pageSize, Long categoryId, String keyword, Long brandId) {
        return Result.ok(goodsService.getGoodsList(pageNum, pageSize, categoryId, keyword, brandId));
    }
    @Operation(summary = "获取商品详情")
    @GetMapping("/detail")
    public Result<GoodsDetailVO> getGoodsDetail(Long goodsId) {
        return Result.ok(goodsService.getGoodsDetail(goodsId));
    }
    @Operation(summary = "获取商品分类树")
    @GetMapping("/category")
    public Result<List<CategoryVO>> getCategoryTree() {
        return Result.ok(categoryService.getCategoryTree());
    }

    @Operation(summary = "分页获取品牌列表")
    @GetMapping("/brand/list")
    public Result<PageVO<BrandVO>> getBrandList(int pageNum, int pageSize) {
        return Result.ok(brandService.getBrandList(pageNum, pageSize));
    }
}

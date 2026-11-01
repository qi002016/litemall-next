package com.litemall.controller;


import com.litemall.domain.DTO.GoodsListQuery;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.BrandVO;
import com.litemall.domain.VO.CategoryVO;
import com.litemall.domain.VO.GoodsDetailVO;
import com.litemall.domain.VO.GoodsVO;
import com.litemall.exception.GoodsApiException;
import com.litemall.service.IBrandService;
import com.litemall.service.ICategoryService;
import com.litemall.service.IGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * <p>
 * 商品基本信息表 前端控制器
 * </p>
 *
 * <p>
 * R1（08 v4 CI-31 / CI-32）：
 * </p>
 * <ul>
 *   <li>查询参数一律用 {@code String} 接收后手工解析——Spring 的类型转换失败会抛
 *       {@code MethodArgumentTypeMismatchException}，那会被全局 advice 转成 HTTP 4xx，
 *       而 C 端要求 <b>HTTP 恒 200</b> + {@code Result.code=400}。</li>
 *   <li>参数非法统一 400 PARAM_INVALID，绝不 500。</li>
 * </ul>
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
    public Result<PageVO<GoodsVO>> getGoodsList(
            @RequestParam(value = "pageNum", required = false) String pageNum,
            @RequestParam(value = "pageSize", required = false) String pageSize,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "brandId", required = false) String brandId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "minP", required = false) String minP,
            @RequestParam(value = "maxP", required = false) String maxP,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "onlyStock", required = false) String onlyStock) {
        GoodsListQuery query = new GoodsListQuery();
        query.setPageNum(parseInt(pageNum, "pageNum"));
        query.setPageSize(parseInt(pageSize, "pageSize"));
        query.setCategoryId(parseLong(categoryId, "categoryId"));
        query.setBrandId(parseLong(brandId, "brandId"));
        query.setKeyword(keyword);
        query.setMinP(parseInt(minP, "minP"));
        query.setMaxP(parseInt(maxP, "maxP"));
        query.setSort(sort);
        query.setOnlyStock(parseBoolean(onlyStock, "onlyStock"));
        return Result.ok(goodsService.getGoodsList(query));
    }

    @Operation(summary = "获取商品详情")
    @GetMapping("/detail")
    public Result<GoodsDetailVO> getGoodsDetail(
            @RequestParam(value = "goodsId", required = false) String goodsId) {
        Long id = parseLong(goodsId, "商品ID");
        if (id == null) {
            throw GoodsApiException.paramInvalid("商品ID不能为空");
        }
        return Result.ok(goodsService.getGoodsDetail(id));
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

    /* ==================== 手工参数解析 ==================== */

    private Integer parseInt(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw GoodsApiException.paramInvalid(name + " 必须是整数");
        }
    }

    private Long parseLong(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw GoodsApiException.paramInvalid(name + " 必须是数字");
        }
    }

    private Boolean parseBoolean(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return Boolean.FALSE;
        }
        throw GoodsApiException.paramInvalid(name + " 必须是布尔值");
    }
}
package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.config.OperationLogContext;
import com.litemall.domain.DTO.BrandCreateDTO;
import com.litemall.domain.DTO.BrandUpdateDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.IdVO;
import com.litemall.domain.dto.BrandAdminDTO;
import com.litemall.domain.dto.BrandAdminVO;
import com.litemall.support.AdminQuerySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.Set;

/**
 * <p>
 * 管理端品牌接口（R2-01：CN-25 / CN-26 / CN-27 / CN-28）。
 * </p>
 * <p>
 * 同 {@link CategoryController}：admin 只做入参归一（页码/每页条数/关键字长度/排序白名单）
 * 与转发，业务判定（品牌名全局唯一、删除前的商品占用）在 goods 侧。金额一律整数分（05 §1.5）。
 * </p>
 *
 * @since 2026-11-01
 */
@RestController
@RequestMapping("/admin/brand")
@RequiredArgsConstructor
@Tag(name = "管理端品牌", description = "R2-01 品牌分页与品牌 CRUD")
public class BrandController {

    /** 操作日志对象类型（05 §4.2 冻结值） */
    private static final String LOG_TARGET_TYPE = "goods";

    /** CN-25 排序白名单（05 §3.5：白名单外落默认 sortOrder，不报错） */
    private static final Set<String> SORT_BY = Set.of("sortOrder", "addTime", "name");

    private final GoodsClient goodsClient;

    /**
     * CN-25 品牌分页列表。
     *
     * @param pageNum  页码（≥1，默认 1）
     * @param pageSize 每页条数（1–100，默认 20）
     * @param keyword  品牌名模糊匹配（≤50）
     * @param sortBy   sortOrder | addTime | name（默认 sortOrder；非法值落默认，不报错）
     * @param isAsc    是否升序（默认 true）
     * @return 分页品牌行
     */
    @Operation(summary = "管理端品牌分页列表")
    @GetMapping("/list")
    public Result<PageVO<BrandAdminVO>> list(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Boolean isAsc) {
        return Result.ok(goodsClient.adminListBrands(
                AdminQuerySupport.pageNum(pageNum),
                AdminQuerySupport.pageSize(pageSize),
                AdminQuerySupport.text(keyword, 50, "keyword"),
                AdminQuerySupport.sortBy(sortBy, SORT_BY, "sortOrder"),
                isAsc == null ? Boolean.TRUE : isAsc));
    }

    /**
     * CN-26 新建品牌。
     *
     * @param body 品牌信息（{@code floorPrice} 为整数分）
     * @return {@code {id}}
     */
    @Operation(summary = "新建品牌")
    @PostMapping("/create")
    public Result<IdVO> create(@Valid @RequestBody BrandCreateDTO body) {
        BrandAdminDTO dto = new BrandAdminDTO();
        dto.setName(trimmed(body.getName()));
        dto.setDesc(body.getDesc());
        dto.setPicUrl(trimmed(body.getPicUrl()));
        dto.setSortOrder(body.getSortOrder());
        dto.setFloorPrice(body.getFloorPrice());
        Long id = goodsClient.adminCreateBrand(dto);
        OperationLogContext.mark("新建品牌：" + body.getName(), LOG_TARGET_TYPE, id);
        return Result.ok(IdVO.of(id));
    }

    /**
     * CN-27 修改品牌（部分更新；{@code updateTime} 回传即 CAS）。
     *
     * @param body 品牌信息（{@code id} 必填）
     * @return 空
     */
    @Operation(summary = "修改品牌")
    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody BrandUpdateDTO body) {
        BrandAdminDTO dto = new BrandAdminDTO();
        dto.setId(body.getId());
        dto.setName(trimmed(body.getName()));
        dto.setDesc(body.getDesc());
        dto.setPicUrl(trimmed(body.getPicUrl()));
        dto.setSortOrder(body.getSortOrder());
        dto.setFloorPrice(body.getFloorPrice());
        dto.setUpdateTime(trimmed(body.getUpdateTime()));
        goodsClient.adminUpdateBrand(dto);
        OperationLogContext.mark("修改品牌", LOG_TARGET_TYPE, body.getId());
        return Result.ok();
    }

    /**
     * CN-28 删除品牌（仍有商品 ⇒ 409 {@code BRAND_IN_USE}「请先移走商品」；否则物理删除）。
     *
     * @param id 品牌ID
     * @return 空
     */
    @Operation(summary = "删除品牌")
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        goodsClient.adminDeleteBrand(id);
        OperationLogContext.mark("删除品牌", LOG_TARGET_TYPE, id);
        return Result.ok();
    }

    /** 名称/图片地址：非空才去空白 */
    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
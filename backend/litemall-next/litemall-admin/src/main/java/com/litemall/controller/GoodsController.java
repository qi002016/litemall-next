package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.domain.DTO.GoodsCreateDTO;
import com.litemall.domain.DTO.GoodsUpdateDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.IdVO;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.support.AdminQuerySupport;
import com.litemall.support.GoodsStatusSupport;
import com.litemall.support.GoodsWriteSupport;
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
 * 管理端商品接口（R1 范围：CI-09 / CI-10 / CI-11 / CI-12）。
 * </p>
 * <p>
 * H5：不持有任何 Mapper，所有商品读写经 {@link GoodsClient} 调 goods 服务的
 * {@code /inner/goods/admin/**}；类目/品牌/规格/SKU 的落库与校验（含 {@code CATEGORY_NOT_LEAF}、
 * {@code SKU_CODE_DUPLICATED}、{@code CONCURRENT_MODIFIED}）全部在 goods 侧，
 * admin 只做「入参校验 + {@code status↔onSale} 映射 + 转发」（03 §6.2）。
 * </p>
 * <p>
 * 与改造前的差异：
 * </p>
 * <ul>
 *   <li>列表筛选参数 {@code name} 改为 {@code keyword}（08 D-18），并补齐
 *       {@code categoryId/brandId/status/onSale/sortBy}；</li>
 *   <li>新建/编辑补齐保留字与规格、SKU 矩阵，金额一律整数分；</li>
 *   <li>新建返回 {@code {id}}（改造前丢弃了返回值）；</li>
 *   <li>{@code status} 与 {@code onSale} 双向映射，矛盾 ⇒ 400 {@code STATUS_ON_SALE_CONFLICT}；</li>
 *   <li>删除（CI-12）保留端点但后台前端不提供入口（D-05），被订单引用时由 goods 侧 409
 *       {@code GOODS_IN_USE}。</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/goods")
@RequiredArgsConstructor
public class GoodsController {

    /** 08 v4 CI-09 商品状态筛选白名单（0 草稿 / 1 在售 / 2 已下架，逗号多值） */
    private static final Set<String> GOODS_STATUS = Set.of("0", "1", "2");

    /** 08 v4 CI-09 排序白名单 */
    private static final Set<String> SORT_BY = Set.of("addTime", "sortOrder", "displayPrice", "totalStock");

    private final GoodsClient goodsClient;

    /**
     * CI-09 商品分页列表。
     *
     * @param pageNum    页码（≥1，默认 1）
     * @param pageSize   每页条数（默认 20，上限 100）
     * @param keyword    商品名称模糊匹配（≤50）
     * @param categoryId 类目ID
     * @param brandId    品牌ID
     * @param status     商品状态，逗号分隔（0,1,2）
     * @param onSale     只按上架筛选（等价 status=1；与 status 同传以 status 为准）
     * @param sortBy     addTime | sortOrder | displayPrice | totalStock
     * @return 分页商品行
     */
    @GetMapping("/list")
    public Result<PageVO<GoodsAdminDTO>> list(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(required = false) String sortBy) {
        String statusFilter = AdminQuerySupport.csvTokens(status, GOODS_STATUS, "status");
        return Result.ok(goodsClient.adminListGoods(
                AdminQuerySupport.pageNum(pageNum),
                AdminQuerySupport.pageSize(pageSize),
                AdminQuerySupport.text(keyword, 50, "keyword"),
                categoryId,
                brandId,
                statusFilter,
                // 08 v4 CI-09：status 与 onSale 同传以 status 为准
                statusFilter == null ? onSale : null,
                AdminQuerySupport.sortBy(sortBy, SORT_BY, "sortOrder")));
    }

    /**
     * CI-10 新建商品。
     *
     * @param body 商品基础信息 + 规格维度 + SKU 矩阵
     * @return {@code {id}}（新商品ID）
     */
    @PostMapping("/create")
    public Result<IdVO> create(@Valid @RequestBody GoodsCreateDTO body) {
        GoodsWriteSupport.validatePrice(body.getMinPrice(), body.getSkus());
        Integer status = GoodsStatusSupport.resolveForCreate(body.getStatus(), body.getOnSale());
        GoodsAdminDTO dto = toAdminDto(body);
        dto.setStatus(status);
        dto.setOnSale(status == GoodsStatusSupport.ON_SALE);
        return Result.ok(IdVO.of(goodsClient.adminCreateGoods(dto)));
    }

    /**
     * CI-11 编辑商品（{@code skus} 传了即全量覆盖；{@code updateTime} 回传则做并发校验）。
     *
     * @param body 商品信息（id 必填）
     * @return 空
     */
    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody GoodsUpdateDTO body) {
        GoodsWriteSupport.validatePriceOnUpdate(body.getMinPrice());
        Integer status = GoodsStatusSupport.resolveForUpdate(body.getStatus(), body.getOnSale());
        GoodsAdminDTO dto = toAdminDto(body);
        dto.setId(body.getId());
        dto.setUpdateTime(AdminQuerySupport.text(body.getUpdateTime(), 32, "updateTime"));
        if (status != null) {
            dto.setStatus(status);
            dto.setOnSale(status == GoodsStatusSupport.ON_SALE);
        }
        goodsClient.adminUpdateGoods(dto);
        return Result.ok();
    }

    /**
     * CI-12 逻辑删除商品（仅草稿且无订单/售后引用；被引用时 goods 侧 409 {@code GOODS_IN_USE}）。
     *
     * @param id 商品ID
     * @return 空
     */
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        goodsClient.adminDeleteGoods(id);
        return Result.ok();
    }

    /** 部分更新时字段可缺省：null 原样透传（goods 侧 applyBaseFields 跳过 null），只在非空时去空白 */
    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private GoodsAdminDTO toAdminDto(GoodsCreateDTO body) {
        GoodsAdminDTO dto = new GoodsAdminDTO();
        dto.setName(trimmed(body.getName()));
        dto.setBrief(body.getBrief());
        dto.setPicUrl(body.getPicUrl());
        dto.setDetail(body.getDetail());
        dto.setGallery(body.getGallery());
        dto.setUnit(body.getUnit());
        dto.setKeywords(body.getKeywords());
        dto.setSortOrder(body.getSortOrder());
        dto.setCategoryId(body.getCategoryId());
        dto.setBrandId(body.getBrandId());
        dto.setMinPrice(body.getMinPrice());
        dto.setCounterPrice(body.getCounterPrice());
        dto.setSpecifications(body.getSpecifications());
        dto.setSkus(body.getSkus());
        return dto;
    }

    private GoodsAdminDTO toAdminDto(GoodsUpdateDTO body) {
        GoodsAdminDTO dto = new GoodsAdminDTO();
        dto.setName(trimmed(body.getName()));
        dto.setBrief(body.getBrief());
        dto.setPicUrl(body.getPicUrl());
        dto.setDetail(body.getDetail());
        dto.setGallery(body.getGallery());
        dto.setUnit(body.getUnit());
        dto.setKeywords(body.getKeywords());
        dto.setSortOrder(body.getSortOrder());
        dto.setCategoryId(body.getCategoryId());
        dto.setBrandId(body.getBrandId());
        dto.setMinPrice(body.getMinPrice());
        dto.setCounterPrice(body.getCounterPrice());
        dto.setSpecifications(body.getSpecifications());
        dto.setSkus(body.getSkus());
        return dto;
    }
}
package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.DTO.BrandAdminQuery;
import com.litemall.domain.PO.Brand;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.BrandVO;
import com.litemall.domain.dto.BrandAdminDTO;
import com.litemall.domain.dto.BrandAdminVO;
import com.litemall.exception.GoodsApiException;
import com.litemall.mapper.BrandMapper;
import com.litemall.mapper.GoodsMapper;
import com.litemall.service.IBrandService;
import com.litemall.support.CatalogSupport;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 品牌商表 服务实现类
 * </p>
 *
 * <p>
 * R2-01 新增管理端四个动作（CN-25~28 / 对内 {@code /inner/goods/admin/brand/*}）。
 * 与分类不同：品牌没有层级，{@code name} 是<b>全局唯一</b>（{@code uk_brand_name(name)}），
 * 唯一的业务前置校验是删除前的商品占用检查（含已下架）。
 * 金额口径按 05 §1.5：库内 {@code DECIMAL(10,2)} 元，出入参一律整数分。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Service
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements IBrandService {

    /** CN-25 关键字长度上限 */
    private static final int MAX_KEYWORD = 50;

    /** 分页默认/上限（与 admin AdminQuerySupport 口径一致） */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private GoodsMapper goodsMapper;

    @Override
    public PageVO<BrandVO> getBrandList(Integer pageNum, Integer pageSize) {
        // 分页归一（06 分页口径）：不传/非法值一律落默认，绝不把 null 传进 Page 造成 500
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1
                ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<Brand> page = new Page<>(num, size);
        Page<Brand> p = lambdaQuery()
                .eq(Brand::getDeleted, false)
                .orderByAsc(Brand::getSortOrder)
                .orderByDesc(Brand::getAddTime)
                .page(page);
        // 金额口径（05 §1.5）：库内元 → 出参整数分，不能再走 BeanUtils 直接拷 BigDecimal
        return PageVO.of(p, this::toBrandVO);
    }

    /**
     * 品牌 PO → C 端 VO：{@code floorPrice} 由库内元换算为整数分。
     *
     * @param brand 品牌行
     * @return C 端品牌 VO
     */
    private BrandVO toBrandVO(Brand brand) {
        BrandVO vo = new BrandVO();
        vo.setId(brand.getId());
        vo.setName(brand.getName());
        vo.setDesc(brand.getDesc());
        vo.setPicUrl(brand.getPicUrl());
        vo.setFloorPrice(CatalogSupport.toCents(brand.getFloorPrice()));
        return vo;
    }

    /* ==================== R2-01：管理端品牌 ==================== */

    @Override
    public PageVO<BrandAdminVO> adminListBrands(BrandAdminQuery query) {
        BrandAdminQuery q = query == null ? new BrandAdminQuery() : query;
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(q.getPageSize(), MAX_PAGE_SIZE);
        String keyword = CatalogSupport.optionalText(q.getKeyword(), MAX_KEYWORD, "关键字");
        boolean asc = q.getIsAsc() == null || q.getIsAsc();
        String sortBy = q.getSortBy() == null ? "sortOrder" : q.getSortBy();

        var wrapper = lambdaQuery().eq(Brand::getDeleted, false);
        if (keyword != null) {
            wrapper.like(Brand::getName, keyword);
        }
        // sortBy 白名单外一律落默认 sortOrder（05 §3.5：非法值忽略，不 400）；id 兜底保证分页稳定
        switch (sortBy) {
            case "addTime" -> wrapper.orderBy(true, asc, Brand::getAddTime);
            case "name" -> wrapper.orderBy(true, asc, Brand::getName);
            default -> wrapper.orderBy(true, asc, Brand::getSortOrder);
        }
        wrapper.orderByAsc(Brand::getId);

        Page<Brand> p = wrapper.page(new Page<>(pageNum, pageSize));
        Map<Long, Long> goodsCounts = goodsCountByBrand(p.getRecords());
        return PageVO.of(p, brand -> toAdminVO(brand, goodsCounts));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBrand(BrandAdminDTO dto) {
        if (dto == null) {
            throw GoodsApiException.paramInvalid("请求体不能为空");
        }
        String name = CatalogSupport.requiredText(dto.getName(), CatalogSupport.MAX_BRAND_NAME, "品牌名称");
        String desc = CatalogSupport.optionalText(dto.getDesc(), CatalogSupport.MAX_DESC, "品牌描述");
        String picUrl = CatalogSupport.optionalText(dto.getPicUrl(), CatalogSupport.MAX_URL, "品牌图片");
        int sortOrder = CatalogSupport.sortOrder(dto.getSortOrder(), 0);
        long floorPrice = CatalogSupport.floorPrice(dto.getFloorPrice());

        LocalDateTime now = LocalDateTime.now();
        Brand entity = new Brand()
                .setName(name)
                .setDesc(desc == null ? "" : desc)
                .setPicUrl(picUrl == null ? "" : picUrl)
                .setSortOrder(sortOrder)
                .setFloorPrice(CatalogSupport.toYuan(floorPrice))
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        try {
            baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // uk_brand_name 兜底（05 §5.2：禁止先查后插）
            throw GoodsApiException.brandNameDuplicated();
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBrand(BrandAdminDTO dto) {
        if (dto == null) {
            throw GoodsApiException.paramInvalid("请求体不能为空");
        }
        if (dto.getId() == null) {
            throw GoodsApiException.paramInvalid("品牌ID不能为空");
        }
        Long id = dto.getId();
        Brand existing = lambdaQuery()
                .eq(Brand::getId, id)
                .eq(Brand::getDeleted, false)
                .one();
        if (existing == null) {
            throw GoodsApiException.brandNotFound();
        }

        Brand update = new Brand().setId(id);
        boolean changed = false;
        if (dto.getName() != null) {
            update.setName(CatalogSupport.requiredText(dto.getName(),
                    CatalogSupport.MAX_BRAND_NAME, "品牌名称"));
            changed = true;
        }
        if (dto.getDesc() != null) {
            String desc = CatalogSupport.optionalText(dto.getDesc(), CatalogSupport.MAX_DESC, "品牌描述");
            update.setDesc(desc == null ? "" : desc);
            changed = true;
        }
        if (dto.getPicUrl() != null) {
            String picUrl = CatalogSupport.optionalText(dto.getPicUrl(), CatalogSupport.MAX_URL, "品牌图片");
            update.setPicUrl(picUrl == null ? "" : picUrl);
            changed = true;
        }
        if (dto.getSortOrder() != null) {
            update.setSortOrder(CatalogSupport.sortOrder(dto.getSortOrder(), 0));
            changed = true;
        }
        if (dto.getFloorPrice() != null) {
            update.setFloorPrice(CatalogSupport.toYuan(CatalogSupport.floorPrice(dto.getFloorPrice())));
            changed = true;
        }
        if (!changed) {
            return;
        }
        update.setUpdateTime(LocalDateTime.now());

        LocalDateTime expect = CatalogSupport.parseUpdateTime(dto.getUpdateTime());
        var wrapper = Wrappers.<Brand>lambdaUpdate()
                .eq(Brand::getId, id)
                .eq(Brand::getDeleted, false);
        if (expect != null) {
            wrapper.eq(Brand::getUpdateTime, expect);
        }
        boolean updated;
        try {
            updated = update(update, wrapper);
        } catch (DuplicateKeyException e) {
            throw GoodsApiException.brandNameDuplicated();
        }
        if (!updated) {
            throw GoodsApiException.concurrentModified("内容已被他人修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBrand(Long id) {
        if (id == null) {
            throw GoodsApiException.paramInvalid("品牌ID不能为空");
        }
        if (lambdaQuery().eq(Brand::getId, id).eq(Brand::getDeleted, false).count() == 0) {
            throw GoodsApiException.brandNotFound();
        }
        // 05 §3.8：仍有商品（含已下架，不含已删除）⇒ 409 BRAND_IN_USE +「请先移走商品」
        Long used = goodsMapper.selectCount(new QueryWrapper<Goods>()
                .eq("deleted", 0)
                .eq("brand_id", id));
        if (used != null && used > 0) {
            throw GoodsApiException.brandInUse();
        }
        // 05 §5.3 A：物理删除
        baseMapper.deleteById(id);
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 批量统计品牌下商品数（含已下架，不含已删除），避免品牌列表 N+1。
     *
     * @param brands 当前页品牌
     * @return brandId → 商品数
     */
    private Map<Long, Long> goodsCountByBrand(List<Brand> brands) {
        if (brands == null || brands.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = brands.stream().map(Brand::getId).toList();
        List<Map<String, Object>> rows = goodsMapper.selectMaps(new QueryWrapper<Goods>()
                .select("brand_id", "COUNT(*) AS cnt")
                .eq("deleted", 0)
                .in("brand_id", ids)
                .groupBy("brand_id"));
        Map<Long, Long> counts = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object brandId = row.get("brand_id");
            Object cnt = row.get("cnt");
            if (brandId != null && cnt != null) {
                counts.put(((Number) brandId).longValue(), ((Number) cnt).longValue());
            }
        }
        return counts;
    }

    private BrandAdminVO toAdminVO(Brand brand, Map<Long, Long> goodsCounts) {
        BrandAdminVO vo = new BrandAdminVO();
        vo.setId(brand.getId());
        vo.setName(brand.getName());
        vo.setDesc(brand.getDesc());
        vo.setPicUrl(brand.getPicUrl());
        vo.setSortOrder(brand.getSortOrder());
        vo.setFloorPrice(CatalogSupport.toCents(brand.getFloorPrice()));
        vo.setGoodsCount(String.valueOf(goodsCounts.getOrDefault(brand.getId(), 0L)));
        vo.setAddTime(CatalogSupport.formatTime(brand.getAddTime()));
        vo.setUpdateTime(CatalogSupport.formatTime(brand.getUpdateTime()));
        return vo;
    }
}
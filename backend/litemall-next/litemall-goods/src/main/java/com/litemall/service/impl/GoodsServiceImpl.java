package com.litemall.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.cache.CacheService;
import com.litemall.domain.PO.Comment;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.PO.GoodsSpecification;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.GoodsDetailVO;
import com.litemall.domain.VO.GoodsVO;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.GoodsMapper;
import com.litemall.service.ICommentService;
import com.litemall.service.IGoodsProductService;
import com.litemall.service.IGoodsService;
import com.litemall.service.IGoodsSpecificationService;
import com.litemall.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements IGoodsService {


    private final IGoodsProductService goodsProductService;
    private final IGoodsSpecificationService specificationService;
    private final ICommentService commentService;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheService cacheService;
    @Override
    public PageVO<GoodsVO> getGoodsList(int pageNum, int pageSize, Long categoryId, String keyword, Long brandId) {
        Page<Goods> page = new Page<>(pageNum, pageSize);

        Page<Goods> p = lambdaQuery()
                .eq(Goods::getIsOnSale, true)
                .eq(Goods::getDeleted, false)
                .eq(categoryId != null, Goods::getCategoryId, categoryId)
                .eq(brandId != null, Goods::getBrandId, brandId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Goods::getName, keyword)
                        .or().like(Goods::getBrief, keyword)
                        .or().like(Goods::getKeywords, keyword))
                .orderByAsc(Goods::getSortOrder)
                .orderByDesc(Goods::getAddTime)
                .page(page);
        List<Goods> goodsList = p.getRecords();
        List<GoodsVO> goodsVOList = goodsList.stream().map(g -> {
            GoodsVO vo = BeanUtils.copyBean(g, GoodsVO.class);
            // minPrice: 优先零售价，缺失时用专柜价兜底
            vo.setMinPrice(g.getRetailPrice() != null ? g.getRetailPrice() : g.getCounterPrice());
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(p.getTotal(), p.getPages(), goodsVOList);
    }

    @Override
    public GoodsDetailVO getGoodsDetail(Long goodsId) {
        if (goodsId == null) {
            throw new BadRequestException("商品不存在");
        }
        // ---- BloomFilter 防穿透（默认关闭，启用步骤见 GoodsBloomFilter 类注释）----
        // if (!bloomFilter.mightContain(String.valueOf(goodsId))) {
        //     return null; // 或 throw new BadRequestException("商品不存在");
        // }
        String key = "goods:detail:" + goodsId;
        // H6：库存不进长 TTL(1800+300s) 的详情缓存——它变化频繁且必须及时可见。
        // 详情缓存只放"变化少"的字段，库存由 goods:stock:<goodsId> 单独承载，
        // 该 key 走 getShared（跳过本地 Caffeine），扣减/回补时执行 Redis 全局精确失效。
        GoodsDetailVO vo = cacheService.getWithLock(key, GoodsDetailVO.class, 1800, 300, () -> {
            Goods goods = getById(goodsId);
            if (goods == null || Boolean.TRUE.equals(goods.getDeleted())) {
                return null; // 空值缓存防穿透
            }
            GoodsDetailVO detail = BeanUtils.copyBean(goods, GoodsDetailVO.class);
            if (StringUtils.hasText(goods.getGallery())) {
                detail.setGallery(JSONUtil.toList(goods.getGallery(), String.class).toArray(new String[0]));
            }
            List<GoodsProduct> products = goodsProductService.lambdaQuery()
                    .eq(GoodsProduct::getGoodsId, goodsId)
                    .eq(GoodsProduct::getDeleted, false)
                    .list();
            if (products != null && !products.isEmpty()) {
                BigDecimal minPrice = products.stream()
                        .map(GoodsProduct::getPrice)
                        .min(BigDecimal::compareTo)
                        .orElse(null);
                detail.setPrice(minPrice);
            }
            List<GoodsSpecification> specList = specificationService.lambdaQuery()
                    .eq(GoodsSpecification::getGoodsId, goodsId)
                    .eq(GoodsSpecification::getDeleted, false)
                    .list();
            detail.setSpecList(specList);
            long cnt = commentService.lambdaQuery()
                    .eq(Comment::getValueId, goodsId)
                    .eq(Comment::getDeleted, false)
                    .count();
            detail.setCommentCount((int) cnt);
            // 缓存内的 stock 恒为空，每次响应由 loadStock 实时补齐
            detail.setStock(null);
            return detail;
        });
        if (vo == null) {
            return null;
        }
        vo.setStock(loadStock(goodsId));
        return vo;
    }

    /**
     * H6：实时库存（短 TTL 30s + 抖动，且跳过本地 Caffeine）。
     * <p>
     * 扣减/回补库存时由 {@code GoodsInnerController} 精确删除 {@code goods:stock:<goodsId>}，
     * 因为是 Redis 级删除（非仅本机），所有节点下一次读取都会回源，库存不再长期滞后。
     */
    private Integer loadStock(Long goodsId) {
        return cacheService.getShared("goods:stock:" + goodsId, Integer.class, 30, 15, () -> {
            List<GoodsProduct> products = goodsProductService.lambdaQuery()
                    .eq(GoodsProduct::getGoodsId, goodsId)
                    .eq(GoodsProduct::getDeleted, false)
                    .list();
            if (products == null || products.isEmpty()) {
                return 0;
            }
            return products.stream()
                    .mapToInt(p -> p.getNumber() == null ? 0 : p.getNumber())
                    .sum();
        });
    }

    /* ==================== H5：管理端商品（admin 服务 Feign 调用） ==================== */

    @Override
    public PageVO<GoodsAdminDTO> adminListGoods(Integer pageNum, Integer pageSize, String name) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<Goods> p = lambdaQuery()
                .eq(Goods::getDeleted, false)
                .like(StringUtils.hasText(name), Goods::getName, name)
                .orderByDesc(Goods::getId)
                .page(new Page<>(pn, ps));
        // 字段与改造前 admin 端的 Map 保持一致：id/name/brief/minPrice/onSale
        return PageVO.of(p, g -> {
            GoodsAdminDTO dto = new GoodsAdminDTO();
            dto.setId(g.getId());
            dto.setName(g.getName());
            dto.setBrief(g.getBrief());
            dto.setPicUrl(g.getPicUrl());
            dto.setMinPrice(g.getRetailPrice());
            dto.setOnSale(g.getIsOnSale());
            return dto;
        });
    }

    @Override
    public long adminCountGoods() {
        return lambdaQuery().eq(Goods::getDeleted, false).count();
    }

    @Override
    public Long adminCreateGoods(GoodsAdminDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new BadRequestException("商品名称不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        Goods goods = new Goods()
                .setName(dto.getName())
                .setBrief(dto.getBrief())
                .setPicUrl(dto.getPicUrl())
                .setRetailPrice(dto.getMinPrice())
                .setIsOnSale(dto.getOnSale() == null ? Boolean.TRUE : dto.getOnSale())
                .setDeleted(Boolean.FALSE)
                .setAddTime(now)
                .setUpdateTime(now);
        save(goods);
        evictGoodsCache(goods.getId());
        return goods.getId();
    }

    @Override
    public void adminUpdateGoods(GoodsAdminDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BadRequestException("商品ID不能为空");
        }
        Goods exists = getById(dto.getId());
        if (exists == null || Boolean.TRUE.equals(exists.getDeleted())) {
            throw new BadRequestException("商品不存在");
        }
        Goods goods = new Goods()
                .setId(dto.getId())
                .setName(dto.getName())
                .setBrief(dto.getBrief())
                .setPicUrl(dto.getPicUrl())
                .setRetailPrice(dto.getMinPrice())
                .setIsOnSale(dto.getOnSale())
                .setUpdateTime(LocalDateTime.now());
        updateById(goods);
        evictGoodsCache(dto.getId());
    }

    @Override
    public void adminDeleteGoods(Long id) {
        if (id == null) {
            throw new BadRequestException("商品ID不能为空");
        }
        Goods exists = getById(id);
        if (exists == null || Boolean.TRUE.equals(exists.getDeleted())) {
            throw new BadRequestException("商品不存在");
        }
        Goods goods = new Goods()
                .setId(id)
                .setDeleted(Boolean.TRUE)
                .setUpdateTime(LocalDateTime.now());
        updateById(goods);
        evictGoodsCache(id);
    }

    /**
     * H5：管理端改动商品后失效详情/实时库存缓存。
     * <p>
     * 走 {@link CacheService#del(String)}（Redis 级删除 + 跨节点失效广播），
     * 与 H6 库存变更的失效方式一致，避免管理端改价/改图后 C 端长时间读到旧详情。
     */
    private void evictGoodsCache(Long goodsId) {
        if (goodsId == null) {
            return;
        }
        cacheService.del("goods:detail:" + goodsId);
        cacheService.del("goods:stock:" + goodsId);
    }
}

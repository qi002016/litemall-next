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
        return cacheService.getWithLock(key, GoodsDetailVO.class, 1800, 300, () -> {
            Goods goods = getById(goodsId);
            if (goods == null || Boolean.TRUE.equals(goods.getDeleted())) {
                return null; // 空值缓存防穿透
            }
            GoodsDetailVO vo = BeanUtils.copyBean(goods, GoodsDetailVO.class);
            if (StringUtils.hasText(goods.getGallery())) {
                vo.setGallery(JSONUtil.toList(goods.getGallery(), String.class).toArray(new String[0]));
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
                vo.setPrice(minPrice);
                int stock = products.stream()
                        .mapToInt(p -> p.getNumber() == null ? 0 : p.getNumber())
                        .sum();
                vo.setStock(stock);
            }
            List<GoodsSpecification> specList = specificationService.lambdaQuery()
                    .eq(GoodsSpecification::getGoodsId, goodsId)
                    .eq(GoodsSpecification::getDeleted, false)
                    .list();
            vo.setSpecList(specList);
            long cnt = commentService.lambdaQuery()
                    .eq(Comment::getValueId, goodsId)
                    .eq(Comment::getDeleted, false)
                    .count();
            vo.setCommentCount((int) cnt);
            return vo;
        });
    }
}

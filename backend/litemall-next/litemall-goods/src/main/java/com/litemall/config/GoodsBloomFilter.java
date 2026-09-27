package com.litemall.config;

import com.litemall.domain.PO.Goods;
import com.litemall.mapper.GoodsMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 商品 ID 布隆过滤器：拦截不存在的商品 id，防缓存穿透。
 *
 * 默认不启用。启用方式：
 *   1. application.yaml 加 bloom.goods.enabled=true
 *   2. 或在下方 @ConditionalOnProperty 注解里把 havingValue 改成 "false"（不推荐，推荐配置开关）
 *   3. 在 GoodsServiceImpl.getGoodsDetail 开头取消注释 bloomFilter.check(goodsId)
 *
 * 实现：BitSet + 3 个 hash，容量 1<<20 = 1,048,576 bit ≈ 128KB，10w 商品误判率 ~1%。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bloom.goods", name = "enabled", havingValue = "true")
public class GoodsBloomFilter {

    private static final int SIZE = 1 << 20;
    private final BitSet bitSet = new BitSet(SIZE);

    private static final List<Function<String, Integer>> HASHES = List.of(
            s -> Math.abs(s.hashCode()),
            s -> Math.abs(s.hashCode() * 31 + 7),
            s -> Math.abs((s.hashCode() ^ (s.hashCode() >>> 16)) * 17 + 3)
    );

    private final GoodsMapper goodsMapper;

    @PostConstruct
    public void init() {
        List<Goods> goods = goodsMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Goods>()
                        .select(Goods::getId)
                        .eq(Goods::getDeleted, false));
        for (Goods g : goods) {
            add(String.valueOf(g.getId()));
        }
        log.info("BloomFilter 预热完成，共 {} 个商品 id", goods.size());
    }

    public void add(String id) {
        for (Function<String, Integer> h : HASHES) {
            bitSet.set(h.apply(id) % SIZE);
        }
    }

    public boolean mightContain(String id) {
        for (Function<String, Integer> h : HASHES) {
            if (!bitSet.get(h.apply(id) % SIZE)) {
                return false;
            }
        }
        return true;
    }

    /** 新商品上架时调用 */
    public void onAdd(Long id) {
        add(String.valueOf(id));
    }

    static class BitSet {
        private final long[] bits;
        BitSet(int size) { this.bits = new long[(size + 63) / 64]; }
        void set(int i) { bits[i >> 6] |= (1L << (i & 63)); }
        boolean get(int i) { return (bits[i >> 6] & (1L << (i & 63))) != 0; }
    }
}

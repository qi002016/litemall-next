package com.litemall.client;

import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 商品服务 Feign 客户端
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
@FeignClient(name = "litemall-goods")
public interface GoodsClient {

    /**
     * 根据货品ID查询商品货品信息（购物车加购等跨服务场景）
     *
     * @param productId 货品/规格ID
     * @return 货品及商品信息
     */
    @GetMapping("/inner/goods/product/info")
    GoodsProductInfoDTO getProductInfo(@RequestParam("productId") Long productId);

    /**
     * 根据商品ID查询默认货品（前端未选择规格、specId 传 0 时取该商品第一个货品）
     *
     * @param goodsId 商品ID
     * @return 货品及商品信息
     */
    @GetMapping("/inner/goods/product/default")
    GoodsProductInfoDTO getDefaultProduct(@RequestParam("goodsId") Long goodsId);

    /**
     * 扣减货品库存（下单场景；库存不足抛异常回滚）
     *
     * @param param key=productId 货品ID, value=number 扣减数量
     */
    @PostMapping("/inner/goods/product/deduct")
    void deductStock(@RequestBody Map<String, Integer> param);

    /**
     * 回补货品库存（取消订单场景）
     *
     * @param param key=productId 货品ID, value=number 回补数量
     */
    @PostMapping("/inner/goods/product/release")
    void releaseStock(@RequestBody Map<String, Integer> param);

    /**
     * 根据商品ID批量查询商品简要信息（收藏/足迹列表展示）
     *
     * @param ids 商品ID集合
     * @return 商品简要信息列表
     */
    @GetMapping("/inner/goods/brief/list")
    List<GoodsBriefDTO> listGoodsBriefByIds(@RequestParam("ids") List<Long> ids);
}

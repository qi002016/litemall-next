package com.litemall.client;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.domain.dto.StockChangeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    void deductStock(@RequestBody Map<String, Object> param);

    /**
     * 回补货品库存（取消订单场景）
     *
     * @param param key=productId 货品ID, value=number 回补数量
     */
    @PostMapping("/inner/goods/product/release")
    void releaseStock(@RequestBody Map<String, Object> param);

    /**
     * M2：批量扣减货品库存（下单场景，一次 RPC 完成多件商品扣减）。
     * <p>
     * 任一货品库存不足即整体失败（商品服务内保证本地事务回滚）。
     *
     * @param dto 库存变更项集合
     */
    @PostMapping("/inner/goods/product/deduct/batch")
    void deductStockBatch(@RequestBody StockChangeDTO dto);

    /**
     * M2：批量回补货品库存（取消订单场景，一次 RPC 完成多件商品回补）。
     *
     * @param dto 库存变更项集合
     */
    @PostMapping("/inner/goods/product/release/batch")
    void releaseStockBatch(@RequestBody StockChangeDTO dto);

    /**
     * 根据商品ID批量查询商品简要信息（收藏/足迹列表展示）
     *
     * @param ids 商品ID集合
     * @return 商品简要信息列表
     */
    @GetMapping("/inner/goods/brief/list")
    List<GoodsBriefDTO> listGoodsBriefByIds(@RequestParam("ids") List<Long> ids);

    /* ==================== H5：管理端商品（admin 服务经 Feign 调用，不再直写 litemall_goods） ==================== */

    /**
     * 管理端商品分页列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param name     商品名称模糊匹配，可空
     * @return 分页商品行
     */
    @GetMapping("/inner/goods/admin/list")
    PageVO<GoodsAdminDTO> adminListGoods(@RequestParam("pageNum") Integer pageNum,
                                         @RequestParam("pageSize") Integer pageSize,
                                         @RequestParam(value = "name", required = false) String name);

    /**
     * 管理端商品总数（首页统计）
     *
     * @return 未删除商品数
     */
    @GetMapping("/inner/goods/admin/count")
    Long adminCountGoods();

    /**
     * H5：管理端新建商品（在 goods 服务内落库并失效商品详情缓存）
     *
     * @param dto 商品信息
     * @return 新建商品ID
     */
    @PostMapping("/inner/goods/admin/create")
    Long adminCreateGoods(@RequestBody GoodsAdminDTO dto);

    /**
     * H5：管理端更新商品（仅更新非空字段）
     *
     * @param dto 商品信息（id 必填）
     */
    @PutMapping("/inner/goods/admin/update")
    void adminUpdateGoods(@RequestBody GoodsAdminDTO dto);

    /**
     * H5：管理端逻辑删除商品
     *
     * @param id 商品ID
     */
    @DeleteMapping("/inner/goods/admin/delete")
    void adminDeleteGoods(@RequestParam("id") Long id);
}

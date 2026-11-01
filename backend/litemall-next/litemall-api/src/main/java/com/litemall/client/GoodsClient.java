package com.litemall.client;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.DeductBatchResultDTO;
import com.litemall.domain.dto.GoodsAdminDTO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.domain.dto.ReleasedBatchResultDTO;
import com.litemall.domain.dto.StockAdminDTO;
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
     * @return 货品及商品信息（R1 起含 skuCode / specValueIds / sellable / number）
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
     * 扣减货品库存（下单场景；库存不足抛 400「库存不足」）
     *
     * @param param key=productId 货品ID, value=number 扣减数量；可选 orderId（仅审计）
     */
    @PostMapping("/inner/goods/product/deduct")
    void deductStock(@RequestBody Map<String, Object> param);

    /**
     * 回补货品库存（取消订单 / 售后退款场景）
     *
     * @param param key=productId 货品ID, value=number 回补数量；可选 orderId、orderGoodsId（仅审计）
     */
    @PostMapping("/inner/goods/product/release")
    void releaseStock(@RequestBody Map<String, Object> param);

    /**
     * 批量扣减货品库存（下单场景，一次 RPC 完成多件商品扣减）。
     * <p>
     * 03 §4.7 D-24 / §4.6 P-31：任一项库存不足 ⇒ 整批回滚并返回
     * {@code deducted=false} + {@code items[].shortage}（HTTP 200，<b>不抛异常</b>），
     * 供 order 侧填 {@code shortage[]} 并保持订单未付款。
     *
     * @param dto 库存变更项集合（可带 orderId）
     * @return 整批扣减结果
     */
    @PostMapping("/inner/goods/product/deduct/batch")
    DeductBatchResultDTO deductStockBatch(@RequestBody StockChangeDTO dto);

    /**
     * 批量回补货品库存（取消订单 / 售后退款场景，一次 RPC 完成多件商品回补）。
     * <p>
     * 重复回补不报错：未实际回补的项 {@code released=false}；幂等判断在 order 侧 {@code restock_status}。
     *
     * @param dto 库存变更项集合（可带 orderId、items[].orderGoodsId）
     * @return 逐项回补结果
     */
    @PostMapping("/inner/goods/product/release/batch")
    ReleasedBatchResultDTO releaseStockBatch(@RequestBody StockChangeDTO dto);

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
     * 管理端商品分页列表（旧签名，R1 保留兼容；新代码请用
     * {@link #adminListGoods(Integer, Integer, String, Long, Long, String, Boolean, String)}）。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param name     商品名称模糊匹配，可空
     * @return 分页商品行
     * @deprecated R1 起请使用带筛选条件的完整签名
     */
    @Deprecated
    @GetMapping("/inner/goods/admin/list")
    PageVO<GoodsAdminDTO> adminListGoods(@RequestParam("pageNum") Integer pageNum,
                                         @RequestParam("pageSize") Integer pageSize,
                                         @RequestParam(value = "name", required = false) String name);

    /**
     * 管理端商品分页列表（08 v4 CI-09 / 对内 CI-47）。
     *
     * @param pageNum   页码（≥1）
     * @param pageSize  每页条数（1–100）
     * @param keyword   商品名称模糊匹配，可空
     * @param categoryId 类目ID，可空
     * @param brandId   品牌ID，可空
     * @param status    商品状态，逗号分隔（0,1,2），可空
     * @param onSale    只按上架筛选（等价 status=1），可空
     * @param sortBy    addTime | sortOrder | displayPrice | totalStock，可空（默认 sortOrder,addTime desc）
     * @return 分页商品行
     */
    @GetMapping("/inner/goods/admin/list")
    PageVO<GoodsAdminDTO> adminListGoods(@RequestParam("pageNum") Integer pageNum,
                                         @RequestParam("pageSize") Integer pageSize,
                                         @RequestParam(value = "keyword", required = false) String keyword,
                                         @RequestParam(value = "categoryId", required = false) Long categoryId,
                                         @RequestParam(value = "brandId", required = false) Long brandId,
                                         @RequestParam(value = "status", required = false) String status,
                                         @RequestParam(value = "onSale", required = false) Boolean onSale,
                                         @RequestParam(value = "sortBy", required = false) String sortBy);

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
     * @param dto 商品信息（含规格维度与 SKU 矩阵）
     * @return 新建商品ID
     */
    @PostMapping("/inner/goods/admin/create")
    Long adminCreateGoods(@RequestBody GoodsAdminDTO dto);

    /**
     * H5：管理端更新商品（含规格维度与 SKU 矩阵全量覆盖）
     *
     * @param dto 商品信息（id 必填）
     */
    @PutMapping("/inner/goods/admin/update")
    void adminUpdateGoods(@RequestBody GoodsAdminDTO dto);

    /**
     * H5：管理端逻辑删除商品（仅草稿且无引用）
     *
     * @param id 商品ID
     */
    @DeleteMapping("/inner/goods/admin/delete")
    void adminDeleteGoods(@RequestParam("id") Long id);

    /* ==================== 库存管理（08 v4 CN-13 / CN-14，admin 经 Feign 消费） ==================== */

    /**
     * 库存列表（CN-13）。
     *
     * @param pageNum      页码（≥1）
     * @param pageSize     每页条数（1–100）
     * @param keyword      商品名或 SKU 码模糊匹配，可空
     * @param categoryId   类目ID，可空
     * @param goodsStatus  商品状态，逗号分隔（0,1,2），可空
     * @param lowStockOnly 只看低库存（sellable ≤ 5），可空
     * @param soldOutOnly  只看售罄（sellable == 0），可空
     * @param sortBy       sellable | number | updateTime，可空（默认 sellable 升序）
     * @return 分页库存行
     */
    @GetMapping("/inner/goods/admin/stock/list")
    PageVO<StockAdminDTO> adminStockList(@RequestParam("pageNum") Integer pageNum,
                                         @RequestParam("pageSize") Integer pageSize,
                                         @RequestParam(value = "keyword", required = false) String keyword,
                                         @RequestParam(value = "categoryId", required = false) Long categoryId,
                                         @RequestParam(value = "goodsStatus", required = false) String goodsStatus,
                                         @RequestParam(value = "lowStockOnly", required = false) Boolean lowStockOnly,
                                         @RequestParam(value = "soldOutOnly", required = false) Boolean soldOutOnly,
                                         @RequestParam(value = "sortBy", required = false) String sortBy);

    /**
     * 库存调整（CN-14）：{@code number} 为绝对值，必须 CAS；
     * {@code number < locked} ⇒ 400 {@code STOCK_ADJUST_CONFLICT}；{@code expectNumber} 不符 ⇒ 409 {@code CONCURRENT_MODIFIED}。
     *
     * @param dto 调整入参（productId、number 必填）
     * @return 调整后的库存行
     */
    @PutMapping("/inner/goods/admin/stock/update")
    StockAdminDTO adminStockUpdate(@RequestBody StockAdminDTO dto);
}
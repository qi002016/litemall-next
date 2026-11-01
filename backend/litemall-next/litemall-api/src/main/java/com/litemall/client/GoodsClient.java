package com.litemall.client;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.BrandAdminDTO;
import com.litemall.domain.dto.BrandAdminVO;
import com.litemall.domain.dto.CategoryAdminDTO;
import com.litemall.domain.dto.CategoryAdminVO;
import com.litemall.domain.dto.CategoryCreateResultDTO;
import com.litemall.domain.dto.CommentAdminVO;
import com.litemall.domain.dto.CommentReplyDTO;
import com.litemall.domain.dto.CommentReplyResultDTO;
import com.litemall.domain.dto.CommentStatusDTO;
import com.litemall.domain.dto.CommentStatusResultDTO;
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

    /* ==================== 分类 / 品牌（R2-01：CN-21~28，admin 经 Feign 消费） ==================== */

    /**
     * 管理端分类树（CN-21 对内）。
     *
     * @param withGoodsCount 是否返回商品数（含已下架、不含已删除）
     * @return 两级分类树（同层按 sortOrder / id 升序）
     */
    @GetMapping("/inner/goods/admin/category/list")
    List<CategoryAdminVO> adminCategoryTree(@RequestParam(value = "withGoodsCount", required = false)
                                            Boolean withGoodsCount);

    /**
     * 管理端新建分类（CN-22 对内）。
     *
     * @param dto 分类信息（pid 缺省 0 表示一级）
     * @return {@code {id, level}}
     */
    @PostMapping("/inner/goods/admin/category/create")
    CategoryCreateResultDTO adminCreateCategory(@RequestBody CategoryAdminDTO dto);

    /**
     * 管理端修改分类（CN-23 对内）：pid 冻结，updateTime 回传即 CAS。
     *
     * @param dto 分类信息（id 必填）
     */
    @PutMapping("/inner/goods/admin/category/update")
    void adminUpdateCategory(@RequestBody CategoryAdminDTO dto);

    /**
     * 管理端删除分类（CN-24 对内）：有子分类 / 有商品 ⇒ 409；否则物理删除。
     *
     * @param id 分类ID
     */
    @DeleteMapping("/inner/goods/admin/category/delete")
    void adminDeleteCategory(@RequestParam("id") Long id);

    /**
     * 管理端品牌分页（CN-25 对内）。
     *
     * @param pageNum  页码（≥1）
     * @param pageSize 每页条数（1–100）
     * @param keyword  品牌名模糊，可空（≤50）
     * @param sortBy   sortOrder | addTime | name，可空（默认 sortOrder）
     * @param isAsc    是否升序，可空（默认 true）
     * @return 分页品牌行
     */
    @GetMapping("/inner/goods/admin/brand/list")
    PageVO<BrandAdminVO> adminListBrands(@RequestParam("pageNum") Integer pageNum,
                                         @RequestParam("pageSize") Integer pageSize,
                                         @RequestParam(value = "keyword", required = false) String keyword,
                                         @RequestParam(value = "sortBy", required = false) String sortBy,
                                         @RequestParam(value = "isAsc", required = false) Boolean isAsc);

    /**
     * 管理端新建品牌（CN-26 对内）。
     *
     * @param dto 品牌信息
     * @return 新品牌ID
     */
    @PostMapping("/inner/goods/admin/brand/create")
    Long adminCreateBrand(@RequestBody BrandAdminDTO dto);

    /**
     * 管理端修改品牌（CN-27 对内）。
     *
     * @param dto 品牌信息（id 必填）
     */
    @PutMapping("/inner/goods/admin/brand/update")
    void adminUpdateBrand(@RequestBody BrandAdminDTO dto);

    /**
     * 管理端删除品牌（CN-28 对内）：仍有商品 ⇒ 409；否则物理删除。
     *
     * @param id 品牌ID
     */
    @DeleteMapping("/inner/goods/admin/brand/delete")
    void adminDeleteBrand(@RequestParam("id") Long id);

    /* ==================== 管理端评价（CN-29~31 对内，05 §3.9~§3.11） ==================== */

    /**
     * 管理端评价分页（CN-29 对内）：{@code total} 与列表<b>含已隐藏</b>，与 C 端口径不同。
     *
     * @param pageNum  页码（≥1）
     * @param pageSize 每页条数（1–100）
     * @param goodsId  商品ID，可空
     * @param keyword  评价内容模糊，可空（≤50）
     * @param star     星级逗号多值（如 {@code 1,2}），可空
     * @param replied  是否已回复，可空
     * @param status   状态逗号多值（0=显示 1=已隐藏），可空
     * @param dateFrom 评价时间起（{@code yyyy-MM-dd HH:mm:ss}），可空
     * @param dateTo   评价时间止（{@code yyyy-MM-dd HH:mm:ss}），可空
     * @return 分页评价行
     */
    @GetMapping("/inner/goods/admin/comment/list")
    PageVO<CommentAdminVO> adminCommentList(
            @RequestParam("pageNum") Integer pageNum,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam(value = "goodsId", required = false) Long goodsId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "star", required = false) String star,
            @RequestParam(value = "replied", required = false) Boolean replied,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo);

    /**
     * 管理端回复评价（CN-30 对内）：覆盖写，{@code reply_time} 每次刷新。
     *
     * @param dto 回复内容
     * @return {@code {commentId, adminContent, replyTime}}
     */
    @PostMapping("/inner/goods/admin/comment/reply")
    CommentReplyResultDTO adminCommentReply(@RequestBody CommentReplyDTO dto);

    /**
     * 管理端隐藏/恢复评价（CN-31 对内）：{@code status} 是目标态，重复提交幂等。
     *
     * @param dto 目标状态
     * @return {@code {commentId, status, statusText}}
     */
    @PostMapping("/inner/goods/admin/comment/status")
    CommentStatusResultDTO adminCommentStatus(@RequestBody CommentStatusDTO dto);
}
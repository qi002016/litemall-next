package com.litemall.client;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.AftersaleAdminDTO;
import com.litemall.domain.dto.AftersaleAdminDetailDTO;
import com.litemall.domain.dto.AftersaleApproveDTO;
import com.litemall.domain.dto.AftersaleApproveResultDTO;
import com.litemall.domain.dto.AftersaleCloseDTO;
import com.litemall.domain.dto.AftersaleCloseResultDTO;
import com.litemall.domain.dto.AftersaleIdDTO;
import com.litemall.domain.dto.AftersaleReceiveResultDTO;
import com.litemall.domain.dto.AftersaleRefundDTO;
import com.litemall.domain.dto.AftersaleRefundResultDTO;
import com.litemall.domain.dto.AftersaleRejectDTO;
import com.litemall.domain.dto.AftersaleRejectResultDTO;
import com.litemall.domain.dto.AftersaleSkipReceiveDTO;
import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.domain.dto.LockedStockDTO;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminDetailDTO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.domain.dto.OrderCloseDTO;
import com.litemall.domain.dto.OrderGoodsStatusDTO;
import com.litemall.domain.dto.OrderPayInfoDTO;
import com.litemall.domain.dto.OrderRefundDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.domain.dto.OrderTryCompleteDTO;
import com.litemall.domain.dto.OrderTryCompleteResultDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import com.litemall.domain.dto.PaySuccessResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * <p>
 * 订单服务 Feign 客户端（pay / admin / goods 服务跨服务调用）。
 * </p>
 * <p>
 * <b>R1 归属：order 域所有者（R1-02）唯一维护本文件</b>（03 §5.5）。R1 变更：
 * <ul>
 *   <li>新增 {@link #adminOrderDetail}、{@link #adminClose}、{@link #adminCloseExpired}、
 *       {@link #adminUpdateShip}、{@link #orderGoodsStatus}、{@link #lockedStock}、{@link #adminOrderList}；</li>
 *   <li>{@link #markPaid} 出参改为 {@link PaySuccessResultDTO}（P-31：实扣失败也 200）；</li>
 *   <li>{@link #adminRefund} 对外端点（CX-03）已下线，方法仅为 admin 模块编译兼容保留，
 *       待 R1-03 的 CX-02（{@code /admin/order/refund} 下线）同批删除，<b>不得在新代码中调用</b>；</li>
 *   <li>{@link #markRefunded}（CX-06）由 R1-04 在 pay 侧改造完成后同批删除。</li>
 * </ul>
 * 对内端点（{@code /inner/**}）失败时保留非 200，Feign 会抛 {@code FeignException}；
 * 因此调用方必须处理 404（RESOURCE_NOT_FOUND）/409（冲突）/400（参数）三类失败。
 * </p>
 */
@FeignClient(name = "litemall-order")
public interface OrderClient {

    /* ==================== 支付域（pay → order） ==================== */

    /**
     * 查询订单支付信息（CI-96，不改契约）。
     *
     * @param orderId 订单ID
     * @return 订单支付信息；订单不存在返回 null
     */
    @GetMapping("/inner/order/pay/info")
    OrderPayInfoDTO getPayInfo(@RequestParam("orderId") Long orderId);

    /**
     * 支付成功（CI-97）：状态闸门 101 → 102 + 实扣库存 + 幂等。
     * <p>
     * 出参 {@code applied/deducted/shortage}：重复回调返回 applied=false；实扣失败返回
     * 200 + deducted=false（订单保持 101），<b>不抛业务异常、不返回 409</b>（P-31）。
     * </p>
     *
     * @param dto 订单ID + 微信支付单号
     * @return 回调处理结果
     */
    @PostMapping("/inner/order/pay/success")
    PaySuccessResultDTO markPaid(@RequestBody PaySuccessDTO dto);

    /**
     * 退款申请：订单置为售后状态，记录退款金额与时间。
     *
     * @deprecated CX-06：{@code /inner/order/pay/refund} 由 R1-04 随 pay 侧改造同批下线，勿在新代码调用。
     */
    @Deprecated(since = "R1", forRemoval = true)
    @PostMapping("/inner/order/pay/refund")
    void markRefunded(@RequestParam("orderId") Long orderId, @RequestParam("amount") BigDecimal amount);

    /* ==================== 管理端订单（admin → order） ==================== */

    /**
     * 管理端订单分页列表（CI-26 / CI-99，同构）。
     *
     * @param pageNo        页码（≥1，默认 1）
     * @param pageSize      每页条数（默认 20，上限 100）
     * @param status        订单状态标识，多值逗号（如 {@code unpaid,undelivered}）
     * @param aftersaleStatus 售后状态标识，多值逗号
     * @param orderSn       订单号精确匹配
     * @param keyword       关键词（订单号/收货人/手机号，≤64）
     * @param userId        用户ID
     * @param addTimeMin    下单时间下界 {@code yyyy-MM-dd HH:mm:ss}
     * @param addTimeMax    下单时间上界
     * @param sortBy        addTime（默认）| orderAmount
     * @param isAsc         是否升序（默认 false）
     * @return 分页订单行
     */
    @GetMapping("/inner/order/admin/list")
    PageVO<OrderAdminDTO> adminOrderList(@RequestParam(value = "pageNo", required = false) Integer pageNo,
                                         @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                         @RequestParam(value = "status", required = false) String status,
                                         @RequestParam(value = "aftersaleStatus", required = false) String aftersaleStatus,
                                         @RequestParam(value = "orderSn", required = false) String orderSn,
                                         @RequestParam(value = "keyword", required = false) String keyword,
                                         @RequestParam(value = "userId", required = false) Long userId,
                                         @RequestParam(value = "addTimeMin", required = false) String addTimeMin,
                                         @RequestParam(value = "addTimeMax", required = false) String addTimeMax,
                                         @RequestParam(value = "sortBy", required = false) String sortBy,
                                         @RequestParam(value = "isAsc", required = false) Boolean isAsc);

    /**
     * 兼容旧签名（admin 模块 R1-05 重写前的 3 参数调用）。
     *
     * @deprecated 用 {@link #adminOrderList} 传完整筛选条件。
     */
    @Deprecated(since = "R1")
    @GetMapping("/inner/order/admin/list")
    PageVO<OrderAdminDTO> adminListOrders(@RequestParam("pageNum") Integer pageNum,
                                          @RequestParam("pageSize") Integer pageSize,
                                          @RequestParam(value = "status", required = false) String status);

    /**
     * CN-33 管理端订单详情（admin 侧包装为 {@code GET /admin/order/{id}}）。
     *
     * @param id 订单ID
     * @return 详情；订单不存在时 order 侧抛 404（FeignException）
     */
    @GetMapping("/inner/order/admin/order/{id}")
    OrderAdminDetailDTO adminOrderDetail(@PathVariable("id") Long id);

    /**
     * 管理端订单统计（CI-100；R2 起支持统计期，见 05-R2 实施口径 §2.2）。
     * <p>
     * R2 改契约（纯增量）：新增可选入参 {@code period}（{@code today|7d|30d}，缺省/非法落
     * {@code 30d}，不报错）；出参 {@code OrderAdminStatsDTO} 只增字段
     * （gmv/orderCount/refundCount/refundAmount/waitShipCount/pendingAftersaleCount/top5/trend，
     * 金额为整数分）。
     * </p>
     *
     * @param period 统计期，可空（等价 {@code 30d}）
     * @return 统计值
     */
    @GetMapping("/inner/order/admin/stats")
    OrderAdminStatsDTO adminOrderStats(@RequestParam(value = "period", required = false) String period);

    /**
     * CI-101 管理端发货：102 → 201（CAS），重复发货 409。
     *
     * @param dto 订单ID + 快递信息
     */
    @PostMapping("/inner/order/admin/ship")
    void adminShip(@RequestBody OrderShipDTO dto);

    /**
     * CN-10 管理端立即关单：101 → 103（close_type=3）+ 释放锁量/券 + 写日志。
     *
     * @param dto 订单ID + 原因 + 操作管理员
     */
    @PostMapping("/inner/order/admin/close")
    void adminClose(@RequestBody OrderCloseDTO dto);

    /**
     * CN-11 超时关单（调度每分钟调用一次）。
     *
     * @param limit  单批上限 1–200，默认 100
     * @param dryRun 只扫描不落库
     * @return 逐单结果
     */
    @PostMapping("/inner/order/admin/close-expired")
    CloseExpiredResultDTO adminCloseExpired(@RequestParam(value = "limit", required = false) Integer limit,
                                            @RequestParam(value = "dryRun", required = false) Boolean dryRun);

    /**
     * CN-12 修改物流：仅 201 且此前未改过（litemall_log 计数，P-22）。
     *
     * @param dto 订单ID + 新快递信息 + 原因
     */
    @PostMapping("/inner/order/admin/update-ship")
    void adminUpdateShip(@RequestBody OrderShipDTO dto);

    /**
     * CN-IN-01 订单行评价校验（只读，供 message 域调用）。
     *
     * @param id     订单行ID
     * @param userId 订单所属用户ID（可选；传入且不匹配 → 404）
     * @return 行状态
     */
    @GetMapping("/inner/order/admin/order-goods/{id}")
    OrderGoodsStatusDTO orderGoodsStatus(@PathVariable("id") Long id,
                                         @RequestParam(value = "userId", required = false) Long userId);

    /**
     * CN-IN-08 订单尝试完成（R2 新增；goods 域 CI-36 评价成功后调用）。
     * <p>
     * 语义：先幂等标记订单行 {@code comment=commentId}，再 CAS 把订单 301 → 501；
     * 只有「当前 301 + 存在未全退行 + 无未退款且未评价行」三条件同时成立才跃迁。
     * 重复调用 / 已非 301 ⇒ {@code changed=false} + 200（不报 409，可安全重试）。
     * </p>
     *
     * @param orderId 订单ID（路径参数）
     * @param body    订单行ID + 评价ID（均必填）
     * @return 订单ID + 回读状态 + 是否发生跃迁
     */
    @PostMapping("/inner/order/admin/order/{orderId}/try-complete")
    OrderTryCompleteResultDTO tryCompleteOrder(@PathVariable("orderId") Long orderId,
                                               @RequestBody OrderTryCompleteDTO body);

    /**
     * R1 管理端退款（CX-03 已下线，仅为 admin 模块编译兼容保留）。
     *
     * @deprecated 退款统一走售后单（CN-IN-02 {@code /inner/pay/refund}），此方法待 CX-02 同批删除。
     */
    @Deprecated(since = "R1", forRemoval = true)
    @PostMapping("/inner/order/admin/refund")
    void adminRefund(@RequestBody OrderRefundDTO dto);

    /* ==================== 库存锁量（goods → order） ==================== */

    /**
     * CN-IN-04 查询 SKU 锁定量（goods 算 {@code sellable} 用；只读快照）。
     *
     * @param productIds 逗号分隔的 productId（≤200）；为 null 表示全部有占用的 SKU
     * @return 锁定量列表（按 productId 升序，无占用为空数组）
     */
    @GetMapping("/inner/order/locked-stock")
    LockedStockDTO lockedStock(@RequestParam(value = "productIds", required = false) String productIds);

    /* ==================== 售后域（admin → order，R1-03） ==================== */

    /**
     * CN-01 管理端售后分页列表。
     * <p>
     * {@code status} 同时接受 8 个标识（applied/waitReturn/...）与数字 0..7，逗号多值可混用；
     * 非法 token / 非法 type / 非法时间格式 → 400 {@code PARAM_INVALID}。
     * </p>
     */
    @GetMapping("/inner/order/admin/aftersale/list")
    PageVO<AftersaleAdminDTO> adminAftersaleList(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) Integer type,
            @RequestParam(value = "aftersaleSn", required = false) String aftersaleSn,
            @RequestParam(value = "orderSn", required = false) String orderSn,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "applyTimeMin", required = false) String applyTimeMin,
            @RequestParam(value = "applyTimeMax", required = false) String applyTimeMax,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "isAsc", required = false) Boolean isAsc);

    /** CN-02 管理端售后详情（含订单摘要、行摘要、时间线）；不存在 → 404 {@code AFTERSALE_NOT_FOUND} */
    @GetMapping("/inner/order/admin/aftersale/detail")
    AftersaleAdminDetailDTO adminAftersaleDetail(@RequestParam("aftersaleId") Long aftersaleId);

    /** CN-03 审核通过：仅退款 0→3；退货退款 0→1 + {@code return_deadline=now+7d}（非 0 → 409） */
    @PostMapping("/inner/order/admin/aftersale/approve")
    AftersaleApproveResultDTO adminAftersaleApprove(@RequestBody AftersaleApproveDTO dto);

    /** CN-04 驳回：0→6，{@code reason} 必填 ≤255（非 0 → 409） */
    @PostMapping("/inner/order/admin/aftersale/reject")
    AftersaleRejectResultDTO adminAftersaleReject(@RequestBody AftersaleRejectDTO dto);

    /** CN-05 确认收到退货：2→3（非 2 → 409） */
    @PostMapping("/inner/order/admin/aftersale/confirm-receive")
    AftersaleReceiveResultDTO adminAftersaleConfirmReceive(@RequestBody AftersaleIdDTO dto);

    /** CN-06 跳过收货：1/2→3，{@code reason} 必填（留日志） */
    @PostMapping("/inner/order/admin/aftersale/skip-receive")
    AftersaleReceiveResultDTO adminAftersaleSkipReceive(@RequestBody AftersaleSkipReceiveDTO dto);

    /**
     * CN-07 售后退款编排（两段式）：2/3 发起；资金成功但回补失败仍是 <b>HTTP 200</b> +
     * {@code restockPending=true}（状态停在 refunding），不返回 409。已 completed 再退 → 409。
     */
    @PostMapping("/inner/order/admin/aftersale/refund")
    AftersaleRefundResultDTO adminAftersaleRefund(@RequestBody AftersaleRefundDTO dto);

    /** CN-08 重试回补：仅 refunding + {@code restock_status=2}，否则 409 */
    @PostMapping("/inner/order/admin/aftersale/retry-restock")
    AftersaleRefundResultDTO adminAftersaleRetryRestock(@RequestBody AftersaleIdDTO dto);

    /** CN-09 关闭售后：0/1/2→5；退款中与终态 → 409 */
    @PostMapping("/inner/order/admin/aftersale/close")
    AftersaleCloseResultDTO adminAftersaleClose(@RequestBody AftersaleCloseDTO dto);
}

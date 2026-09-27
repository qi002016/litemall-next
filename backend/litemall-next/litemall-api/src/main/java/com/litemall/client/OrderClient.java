package com.litemall.client;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.domain.dto.OrderPayInfoDTO;
import com.litemall.domain.dto.OrderRefundDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * <p>
 * 订单服务 Feign 客户端（pay 服务跨服务调用）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@FeignClient(name = "litemall-order")
public interface OrderClient {

    /**
     * 查询订单支付信息（支付下单/结果查询）
     *
     * @param orderId 订单ID
     * @return 订单支付信息；订单不存在返回 null
     */
    @GetMapping("/inner/order/pay/info")
    OrderPayInfoDTO getPayInfo(@RequestParam("orderId") Long orderId);

    /**
     * 支付成功：订单 未付款 → 待发货，并写入支付时间与支付单号
     *
     * @param dto 订单ID + 微信支付单号
     */
    @PostMapping("/inner/order/pay/success")
    void markPaid(@RequestBody PaySuccessDTO dto);

    /**
     * 退款申请：订单置为售后状态，记录退款金额与时间
     *
     * @param orderId 订单ID
     * @param amount  退款金额
     */
    @PostMapping("/inner/order/pay/refund")
    void markRefunded(@RequestParam("orderId") Long orderId, @RequestParam("amount") BigDecimal amount);

    /* ==================== H5：管理端订单（admin 服务经 Feign 调用，不再直写 litemall_order） ==================== */

    /**
     * 管理端订单分页列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   文本状态 unpaid/undelivered/shipped/confirmed/refund，空为全部
     * @return 分页订单行
     */
    @GetMapping("/inner/order/admin/list")
    PageVO<OrderAdminDTO> adminListOrders(@RequestParam("pageNum") Integer pageNum,
                                          @RequestParam("pageSize") Integer pageSize,
                                          @RequestParam(value = "status", required = false) String status);

    /**
     * 管理端首页订单统计（今日订单数 + 待发货数）
     *
     * @return 统计值
     */
    @GetMapping("/inner/order/admin/stats")
    OrderAdminStatsDTO adminOrderStats();

    /**
     * H5：管理端发货 —— 由 order 服务校验状态（仅 102 待发货 → 201 已发货）
     *
     * @param dto 订单ID + 快递信息
     */
    @PostMapping("/inner/order/admin/ship")
    void adminShip(@RequestBody OrderShipDTO dto);

    /**
     * H5：管理端退款 —— 由 order 服务校验状态（102/201/301 → 401），
     * 并完成库存回补与优惠券回退；401 幂等无补偿，402 拒绝。
     *
     * @param dto 订单ID + 退款金额/备注
     */
    @PostMapping("/inner/order/admin/refund")
    void adminRefund(@RequestBody OrderRefundDTO dto);
}

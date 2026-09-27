package com.litemall.controller;

import com.litemall.client.OrderClient;
import com.litemall.domain.DTO.OrderRefundReqDTO;
import com.litemall.domain.DTO.OrderShipReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderRefundDTO;
import com.litemall.domain.dto.OrderShipDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 管理端订单接口。
 * </p>
 * <p>
 * H5：本类不再持有 {@code AdminOrderMapper}，所有订单读写都经 {@link OrderClient}
 * 调用 order 服务的 {@code /inner/order/admin/**}；状态流转（发货 201、退款 401/402）
 * 的合法性校验、库存回补与优惠券回退全部在 order 服务内完成。
 * 对外路径/方法/字段与改造前完全一致。
 * </p>
 */
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderClient orderClient;

    @GetMapping("/list")
    public Result<PageVO<OrderAdminDTO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(orderClient.adminListOrders(pageNum, pageSize, status));
    }

    @PostMapping("/ship")
    public Result<Void> ship(@Valid @RequestBody OrderShipReqDTO body) {
        OrderShipDTO dto = new OrderShipDTO();
        dto.setOrderId(body.getOrderId());
        dto.setShipChannel(body.getShipChannel());
        dto.setShipSn(body.getShipSn());
        orderClient.adminShip(dto);
        return Result.ok();
    }

    @PostMapping("/refund")
    public Result<Void> refund(@Valid @RequestBody OrderRefundReqDTO body) {
        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderId(body.getOrderId());
        dto.setRefundAmount(body.getRefundAmount());
        dto.setRefundContent(body.getRefundContent());
        orderClient.adminRefund(dto);
        return Result.ok();
    }
}
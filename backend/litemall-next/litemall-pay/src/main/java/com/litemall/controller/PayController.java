package com.litemall.controller;

import com.litemall.domain.DTO.PayWechatDTO;
import com.litemall.domain.DTO.RefundDTO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.PayParamsVO;
import com.litemall.domain.VO.PayResultVO;
import com.litemall.domain.VO.RefundVO;
import com.litemall.service.IPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 支付接口
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@RestController
@RequestMapping("/app/pay")
@Tag(name = "pay", description = "支付相关接口")
@RequiredArgsConstructor
public class PayController {

    private final IPayService payService;

    /**
     * CI-103：微信支付下单（演示支付，可选成功/失败）
     */
    @PostMapping("/wechat")
    @Operation(summary = "微信支付下单（演示）")
    public Result<PayParamsVO> wechat(@RequestBody @Valid PayWechatDTO dto) {
        return Result.ok(payService.wechatPay(dto.getOrderId(), dto.getMode()));
    }

    /**
     * CI-104：微信支付回调（验签失败 → 400 PARAM_INVALID，不返回 401）
     */
    @PostMapping("/notify/wechat")
    @Operation(summary = "微信支付回调")
    public Result<Void> notifyWechat(@RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
                                     @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                     @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                     @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                     @RequestBody String rawBody) {
        payService.notifyWechat(serial, timestamp, nonce, signature, rawBody);
        return Result.ok();
    }

    /**
     * CI-105：申请退款（保留，仅演示直付单可用；R1 售后退款一律走 CN-07）
     */
    @PostMapping("/refund")
    @Operation(summary = "申请退款（演示直付单）")
    public Result<RefundVO> refund(@RequestBody @Valid RefundDTO dto) {
        return Result.ok(payService.refund(dto));
    }

    /**
     * CI-106：查询支付结果
     */
    @GetMapping("/result")
    @Operation(summary = "查询支付结果")
    public Result<PayResultVO> result(@RequestParam("orderId") Long orderId) {
        return Result.ok(payService.queryResult(orderId));
    }
}

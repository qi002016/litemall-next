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
 * 支付服务接口
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@RestController
@RequestMapping("/app/pay")
@RequiredArgsConstructor
@Tag(name = "pay", description = "支付相关接口")
public class PayController {

    private final IPayService payService;

    @Operation(summary = "微信支付下单（模拟）")
    @PostMapping("/wechat")
    public Result<PayParamsVO> wechat(@RequestBody @Valid PayWechatDTO dto) {
        return Result.ok(payService.wechatPay(dto.getOrderId()));
    }

    @Operation(summary = "微信支付回调（白名单：免登录但必须验签）")
    @PostMapping("/notify/wechat")
    public Result<Void> notifyWechat(
            @RequestBody String rawBody,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature) {
        // H2：不再直接信任回调体，先经 PayNotifyVerifier 验签（生产为微信平台证书验签）
        payService.notifyWechat(serial, timestamp, nonce, signature, rawBody);
        return Result.ok(null);
    }

    @Operation(summary = "申请退款")
    @PostMapping("/refund")
    public Result<RefundVO> refund(@RequestBody @Valid RefundDTO dto) {
        return Result.ok(payService.refund(dto));
    }

    @Operation(summary = "查询支付结果")
    @GetMapping("/result")
    public Result<PayResultVO> result(@RequestParam("orderId") Long orderId) {
        return Result.ok(payService.queryResult(orderId));
    }
}

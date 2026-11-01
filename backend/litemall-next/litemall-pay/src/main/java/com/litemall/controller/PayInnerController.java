package com.litemall.controller;

import com.litemall.domain.dto.PayRefundDTO;
import com.litemall.domain.dto.PayRefundResultDTO;
import com.litemall.service.IPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 支付内部接口（供 order 服务调用，/inner/** 已由 InnerApiInterceptor 校验内部令牌）
 * </p>
 * <p>
 * 契约见 07 §5.5（CN-IN-02）：返回裸类型、非 200 HTTP、不带 errorCode，故此处不做 Result 包装。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@RestController
@RequestMapping("/inner/pay")
@Tag(name = "pay-inner", description = "支付内部接口")
@RequiredArgsConstructor
public class PayInnerController {

    private final IPayService payService;

    /**
     * CN-IN-02：售后退款（幂等键 aftersaleId；不回调 order）
     */
    @PostMapping("/refund")
    @Operation(summary = "售后退款")
    public PayRefundResultDTO refund(@RequestBody PayRefundDTO dto) {
        return payService.refundFunds(dto);
    }
}
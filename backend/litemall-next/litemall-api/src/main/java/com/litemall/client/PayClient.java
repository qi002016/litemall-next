package com.litemall.client;

import com.litemall.domain.dto.PayRefundDTO;
import com.litemall.domain.dto.PayRefundResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * <p>
 * 支付服务 Feign 客户端（order 服务跨服务调用）
 * </p>
 * <p>
 * 契约见 07 §5.5（CN-IN-02）与 docs/impl/03-R1实施口径.md §4：签名由 R1-04 独占，order 侧只调用不得改签名。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@FeignClient(name = "litemall-pay")
public interface PayClient {

    /**
     * 售后退款（幂等：同一 aftersaleId 重复调用返回同一 refundId）
     *
     * @param dto 售后单ID + 订单ID + 退款金额（整数分）
     * @return 退款单ID与退款状态
     */
    @PostMapping("/inner/pay/refund")
    PayRefundResultDTO refund(@RequestBody PayRefundDTO dto);
}
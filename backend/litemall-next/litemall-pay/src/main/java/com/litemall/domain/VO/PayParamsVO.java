package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 微信支付参数VO（wx.requestPayment）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "微信支付参数")
public class PayParamsVO {

    @Schema(description = "时间戳（秒）")
    private String timeStamp;

    @Schema(description = "随机串")
    private String nonceStr;

    @Schema(description = "固定值 prepay_id=wx...")
    private String package_;

    @Schema(description = "签名算法，固定 RSA")
    private String signType;

    @Schema(description = "签名")
    private String paySign;

    /**
     * CI-103 新增：R1 为演示支付，恒为 true
     */
    @Schema(description = "是否演示支付，R1 恒为 true")
    private Boolean demo;

    /**
     * CI-103 新增：演示支付结果 success / fail
     */
    @Schema(description = "演示支付结果：success / fail")
    private String payResult;
}

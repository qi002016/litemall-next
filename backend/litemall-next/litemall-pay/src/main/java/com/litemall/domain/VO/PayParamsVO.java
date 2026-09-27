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
}

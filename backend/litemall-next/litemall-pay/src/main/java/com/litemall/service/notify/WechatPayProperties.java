package com.litemall.service.notify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * H2：微信支付回调验签配置（全部来自环境变量，不入库）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hm.pay.wechat")
public class WechatPayProperties {

    /** 是否启用真实验签；false 时仅 dev profile 允许使用明文解析器 */
    private boolean enabled = false;

    /** 微信支付平台证书（PEM），如 file:${HM_WX_PAY_CERT} */
    private Resource platformCert;

    /** APIv3 密钥（32 字节），用于解密 resource */
    private String apiV3Key;
}

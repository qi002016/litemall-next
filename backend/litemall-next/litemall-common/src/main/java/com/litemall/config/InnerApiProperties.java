package com.litemall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * H3：内部接口({@code /inner/**})调用鉴权配置。
 * <p>
 * 各服务通过统一的请求头携带共享密钥；密钥由环境变量 {@code HM_INTERNAL_TOKEN} 注入，
 * 不写入仓库。非 dev 环境未配置时启动失败（见 {@code MvcConfig}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hm.internal")
public class InnerApiProperties {

    /** 内部调用共享密钥请求头 */
    public static final String HEADER = "X-Internal-Token";

    /** 共享密钥；dev 允许为空（放行并告警），非 dev 为空则启动失败 */
    private String token;
}

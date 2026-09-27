package com.litemall.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * M11：JWT/密钥库配置（单一实现，替代 gateway/user/admin 的三份复制）。
 * <p>
 * 口令不设默认值：缺失时启动失败（H4 fail-fast），由环境变量 HM_JKS_PASSWORD 注入。
 */
@Data
@ConfigurationProperties(prefix = "hm.jwt")
public class JwtProperties {
    private Resource location;
    private String password;
    private String alias;
    private Duration tokenTTL = Duration.ofMinutes(10);
}
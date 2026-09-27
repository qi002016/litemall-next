package com.litemall.jwt;

import cn.hutool.core.util.StrUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.encrypt.KeyStoreKeyFactory;

import java.security.KeyPair;

/**
 * M11：JWT 能力的自动装配（gateway / user / admin 引入依赖即可，无需各自写 SecurityConfig.keyPair）。
 * <p>
 * H4：密钥库位置/别名/口令必须齐全，否则启动失败（fail-fast），
 * 避免以空口令构造出"看似可用"的签名器。
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeyPair jwtKeyPair(JwtProperties properties) {
        if (properties.getLocation() == null
                || StrUtil.isBlank(properties.getAlias())
                || StrUtil.isBlank(properties.getPassword())) {
            throw new IllegalStateException(
                    "H4：必须配置 hm.jwt.location / hm.jwt.alias / hm.jwt.password"
                            + "（对应环境变量 HM_JKS_LOCATION / HM_JKS_ALIAS / HM_JKS_PASSWORD），否则启动失败");
        }
        KeyStoreKeyFactory keyStoreKeyFactory = new KeyStoreKeyFactory(
                properties.getLocation(),
                properties.getPassword().toCharArray());
        return keyStoreKeyFactory.getKeyPair(
                properties.getAlias(),
                properties.getPassword().toCharArray());
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTool jwtTool(KeyPair jwtKeyPair) {
        return new JwtTool(jwtKeyPair);
    }
}
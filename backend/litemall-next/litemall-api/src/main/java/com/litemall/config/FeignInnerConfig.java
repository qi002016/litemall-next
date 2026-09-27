package com.litemall.config;

import cn.hutool.core.util.StrUtil;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * H3：Feign 调用统一注入内部接口密钥头。
 * <p>
 * 所有经 {@code litemall-api} 契约发起的跨服务调用自动携带 {@code X-Internal-Token}，
 * 供下游服务的 {@code InnerApiInterceptor} 校验；调用方无需感知。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(RequestInterceptor.class)
public class FeignInnerConfig {

    private final InnerApiProperties innerApiProperties;

    @Bean
    public RequestInterceptor innerTokenRequestInterceptor() {
        return template -> {
            String token = innerApiProperties.getToken();
            if (StrUtil.isNotBlank(token)) {
                template.header(InnerApiProperties.HEADER, token);
            }
        };
    }
}

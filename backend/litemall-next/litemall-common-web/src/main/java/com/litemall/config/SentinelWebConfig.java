package com.litemall.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.SentinelWebPrefixInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sentinel 请求方式前缀拦截器配置。
 *
 * <p>问题：SCA 默认装配的 {@code SentinelWebInterceptor} 以 URL 匹配模式
 * （{@code HandlerMapping.bestMatchingPattern}）作为资源名，忽略请求方式，
 * 导致同路径的查询/删除/修改等接口在控制台被识别为同一个簇点资源。</p>
 *
 * <p>方案：改用 {@link SentinelWebPrefixInterceptor}，其资源名形如
 * {@code GET:/carts/{id}}、{@code DELETE:/carts/{id}}，能区分请求方式。</p>
 *
 * <p><b>注意</b>：使用本配置时，各微服务需关闭 SCA 默认拦截器，避免重复拦截：
 * {@code spring.cloud.sentinel.filter.enabled=false}。</p>*/


@Configuration
@ConditionalOnClass({DispatcherServlet.class, SentinelWebPrefixInterceptor.class})
public class SentinelWebConfig implements WebMvcConfigurer {

    @Bean
    public SentinelWebPrefixInterceptor sentinelWebPrefixInterceptor() {
        return new SentinelWebPrefixInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sentinelWebPrefixInterceptor()).addPathPatterns("/**");
    }
}

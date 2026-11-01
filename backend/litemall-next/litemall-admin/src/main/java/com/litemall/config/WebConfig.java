package com.litemall.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final OperationLogInterceptor operationLogInterceptor;

    private final LogFailureMarker logFailureMarker;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operationLogInterceptor).addPathPatterns("/admin/**");
    }

    @Override
    public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        // D-36：对外端点恒 HTTP 200，失败只体现在 Result.code —— 先打「本次请求失败」标记，
        // 再由统一错误处理生成响应（本 resolver 自身不处理，return null）
        resolvers.add(logFailureMarker);
    }
}
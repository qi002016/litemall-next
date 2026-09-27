package com.litemall.config;

import cn.hutool.core.util.StrUtil;
import com.litemall.interceptor.InnerApiInterceptor;
import com.litemall.interceptor.UserInfoInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
@ConditionalOnClass(DispatcherServlet.class)
public class MvcConfig implements WebMvcConfigurer {

    private final InnerApiProperties innerApiProperties;

    /**
     * 是否为 dev 环境。在构造期确定，避免依赖 {@code @PostConstruct} 与
     * {@code addInterceptors} 回调之间的执行顺序（后者由 HandlerMapping 初始化触发）。
     */
    private final boolean devProfile;

    public MvcConfig(InnerApiProperties innerApiProperties, Environment environment) {
        this.innerApiProperties = innerApiProperties;
        this.devProfile = environment.acceptsProfiles(Profiles.of("dev"));
        // H3：fail-fast —— 非 dev 环境未配置内部密钥直接启动失败，避免内部接口裸奔
        if (!devProfile && StrUtil.isBlank(innerApiProperties.getToken())) {
            throw new IllegalStateException(
                    "H3：非 dev 环境必须配置 hm.internal.token(环境变量 HM_INTERNAL_TOKEN)，用于 /inner/** 内部接口鉴权");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInfoInterceptor());
        registry.addInterceptor(new InnerApiInterceptor(innerApiProperties, devProfile))
                .addPathPatterns("/inner/**");
    }
}
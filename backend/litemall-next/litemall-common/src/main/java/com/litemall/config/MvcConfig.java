package com.litemall.config;

import cn.hutool.core.util.StrUtil;
import com.litemall.interceptor.InnerApiInterceptor;
import com.litemall.interceptor.UserInfoInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@RequiredArgsConstructor
public class MvcConfig implements WebMvcConfigurer {

    private final InnerApiProperties innerApiProperties;
    private final Environment environment;

    private boolean devProfile;

    @PostConstruct
    void validateInnerToken() {
        devProfile = environment.acceptsProfiles(Profiles.of("dev"));
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

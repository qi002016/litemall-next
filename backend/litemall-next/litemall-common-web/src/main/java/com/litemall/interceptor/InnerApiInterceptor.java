package com.litemall.interceptor;

import cn.hutool.core.util.StrUtil;
import com.litemall.config.InnerApiProperties;
import com.litemall.exception.ForbiddenException;
import com.litemall.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * H3：{@code /inner/**} 内部接口鉴权。
 * <p>
 * 校验网关/上游服务注入的 {@code X-Internal-Token} 共享密钥头；
 * dev 环境允许密钥为空（放行并告警一次），非 dev 为空由 {@code MvcConfig} 在启动时拒绝。
 */
@Slf4j
@RequiredArgsConstructor
public class InnerApiInterceptor implements HandlerInterceptor {

    private final InnerApiProperties properties;
    private final boolean devProfile;
    private volatile boolean warned = false;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String expected = properties.getToken();
        if (StrUtil.isBlank(expected)) {
            if (devProfile) {
                if (!warned) {
                    warned = true;
                    log.warn("[H3] hm.internal.token 未配置，dev 环境放行 /inner/**；生产环境必须配置 HM_INTERNAL_TOKEN");
                }
                return true;
            }
            throw new ForbiddenException("内部接口未配置鉴权密钥");
        }
        String actual = request.getHeader(InnerApiProperties.HEADER);
        if (!expected.equals(actual)) {
            throw new UnauthorizedException("内部接口鉴权失败");
        }
        return true;
    }
}

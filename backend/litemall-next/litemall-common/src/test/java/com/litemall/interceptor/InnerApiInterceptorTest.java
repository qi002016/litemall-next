package com.litemall.interceptor;

import com.litemall.config.InnerApiProperties;
import com.litemall.exception.ForbiddenException;
import com.litemall.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H3：{@code /inner/**} 共享密钥拦截器单元测试。
 */
class InnerApiInterceptorTest {

    private static InnerApiProperties props(String token) {
        InnerApiProperties p = new InnerApiProperties();
        p.setToken(token);
        return p;
    }

    private static MockHttpServletRequest requestWith(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/inner/order/markPaid");
        if (headerValue != null) {
            request.addHeader(InnerApiProperties.HEADER, headerValue);
        }
        return request;
    }

    @Test
    @DisplayName("H3：dev 环境未配置密钥时放行（并只告警一次）")
    void devWithoutToken_allows() {
        InnerApiInterceptor interceptor = new InnerApiInterceptor(props(""), true);

        assertThat(interceptor.preHandle(requestWith(null), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    @DisplayName("H3：非 dev 环境未配置密钥时必须拒绝")
    void prodWithoutToken_forbidden() {
        InnerApiInterceptor interceptor = new InnerApiInterceptor(props("   "), false);

        assertThatThrownBy(() -> interceptor.preHandle(requestWith(null), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("H3：已配置密钥时，密钥正确才放行")
    void tokenMustMatch() {
        InnerApiInterceptor interceptor = new InnerApiInterceptor(props("s3cret"), false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(requestWith("s3cret"), response, new Object())).isTrue();
    }

    @Test
    @DisplayName("H3：已配置密钥时，缺失或错误的密钥必须拒绝")
    void wrongToken_unauthorized() {
        InnerApiInterceptor interceptor = new InnerApiInterceptor(props("s3cret"), false);

        assertThatThrownBy(() -> interceptor.preHandle(requestWith(null), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> interceptor.preHandle(requestWith("guess"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }
}

package com.litemall.filter;

import com.litemall.domain.TokenPrincipal;
import com.litemall.jwt.JwtProperties;
import com.litemall.jwt.JwtTool;
import com.litemall.support.AdminSessionVersion;
import com.litemall.support.AdminTokenIssuer;
import com.litemall.utils.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@code AdminAuthFilter} 的 P-1 会话版本校验（05-R2 实施口径 §5.12）。
 * <p>
 * 用真实 RSA 密钥对签令牌（与生产同构：{@link AdminTokenIssuer} 签发、
 * {@link JwtTool} 验签），只把「Redis 当前版本」用 mock 注入，避免假令牌掩盖验签逻辑。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthFilterTest {

    private static final Long ADMIN_ID = 7L;

    @Mock
    private AdminSessionVersion sessionVersion;

    private JwtTool jwtTool;
    private AdminTokenIssuer tokenIssuer;
    private AdminAuthFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        jwtTool = new JwtTool(keyPair);
        JwtProperties properties = new JwtProperties();
        properties.setTokenTTL(Duration.ofMinutes(10));
        tokenIssuer = new AdminTokenIssuer(keyPair, properties);
        filter = new AdminAuthFilter(jwtTool, sessionVersion);
    }

    private static MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    @Test
    @DisplayName("ver 一致 → 放行，且请求内可读到当前管理员")
    void matchingVersionPasses() throws Exception {
        String token = tokenIssuer.issue(ADMIN_ID, 3);
        when(sessionVersion.current(ADMIN_ID)).thenReturn(3);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Long> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(UserContext.getUser());

        filter.doFilter(request("/admin/dashboard/stats", token), response, chain);

        assertThat(seen.get()).isEqualTo(ADMIN_ID);
        assertThat(response.getStatus()).isEqualTo(200);
        // 请求结束后必须清理 ThreadLocal，避免线程复用串号
        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    @DisplayName("INCR 后旧令牌（ver 落后）→ 401 SESSION_EXPIRED，不再放行")
    void staleVersionRejected() throws Exception {
        String token = tokenIssuer.issue(ADMIN_ID, 0);
        when(sessionVersion.current(ADMIN_ID)).thenReturn(1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/admin/account/list", token), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("登录状态已失效，请重新登录")
                .contains("SESSION_EXPIRED");
        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    @DisplayName("老令牌没有 ver 声明按 0 处理：版本仍为 0 时可用（兼容存量会话）")
    void legacyTokenWithoutVerStillWorks() throws Exception {
        String token = jwtTool.createToken(ADMIN_ID, TokenPrincipal.ROLE_ADMIN, TokenPrincipal.AUD_ADMIN,
                Duration.ofMinutes(10));
        when(sessionVersion.current(ADMIN_ID)).thenReturn(0);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/admin/account/list", token), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("老令牌在版本已 +1 后 → 401（禁用/改密后存量令牌同样失效）")
    void legacyTokenRejectedAfterIncrease() throws Exception {
        String token = jwtTool.createToken(ADMIN_ID, TokenPrincipal.ROLE_ADMIN, TokenPrincipal.AUD_ADMIN,
                Duration.ofMinutes(10));
        when(sessionVersion.current(ADMIN_ID)).thenReturn(2);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/admin/account/list", token), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_EXPIRED");
    }

    @Test
    @DisplayName("Redis 不可读（版本未知）→ 降级放行，不放大为全站 401")
    void redisUnavailableDegrades() throws Exception {
        String token = tokenIssuer.issue(ADMIN_ID, 0);
        when(sessionVersion.current(ADMIN_ID)).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/admin/account/list", token), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("无令牌/坏令牌 → 401「未登录或令牌无效」（data 为 null）")
    void invalidTokenRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/admin/account/list", "not-a-jwt"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("未登录或令牌无效").contains("\"data\":null");
    }

    @Test
    @DisplayName("C 端令牌（role=user）→ 403 无权访问管理端")
    void userTokenForbidden() throws Exception {
        String token = jwtTool.createToken(ADMIN_ID, TokenPrincipal.ROLE_USER, TokenPrincipal.AUD_APP,
                Duration.ofMinutes(10));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/admin/account/list", token), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("无权访问管理端");
    }

    @Test
    @DisplayName("登录路径匿名放行（不过版本校验）")
    void loginPathIsAnonymous() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/admin/auth/login", null), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("下游 ServletException 不得被误判成 401（异常交给统一异常处理）")
    void downstreamServletExceptionPropagates() {
        String token = tokenIssuer.issue(ADMIN_ID, 0);
        when(sessionVersion.current(ADMIN_ID)).thenReturn(0);
        FilterChain chain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request("/admin/account/list", token),
                new MockHttpServletResponse(), chain))
                .isInstanceOf(ServletException.class)
                .hasMessage("boom");
    }
}
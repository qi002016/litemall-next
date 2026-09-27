package com.litemall.filter;

import com.litemall.config.AuthProperties;
import com.litemall.domain.TokenPrincipal;
import com.litemall.exception.UnauthorizedException;
import com.litemall.utils.JwtTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H1：网关路径级 RBAC 与请求头剥离的单元测试（纯 Mockito，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class AuthGlobalFilterTest {

    @Mock
    private JwtTool jwtTool;

    @Mock
    private GatewayFilterChain chain;

    private AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setExcludePaths(List.of("/app/auth/login", "/app/pay/notify/wechat"));
        AuthProperties.RoleRule adminRule = new AuthProperties.RoleRule();
        adminRule.setPattern("/admin/**");
        adminRule.setRole(TokenPrincipal.ROLE_ADMIN);
        adminRule.setAud(TokenPrincipal.AUD_ADMIN);
        AuthProperties.RoleRule appRule = new AuthProperties.RoleRule();
        appRule.setPattern("/app/**");
        appRule.setRole(TokenPrincipal.ROLE_USER);
        appRule.setAud(TokenPrincipal.AUD_APP);
        authProperties.setRoleRules(List.of(adminRule, appRule));
        filter = new AuthGlobalFilter(authProperties, jwtTool, new AntPathMatcher());
    }

    private TokenPrincipal userToken() {
        return new TokenPrincipal(7L, TokenPrincipal.ROLE_USER, TokenPrincipal.AUD_APP);
    }

    private TokenPrincipal adminToken() {
        return new TokenPrincipal(9L, TokenPrincipal.ROLE_ADMIN, TokenPrincipal.AUD_ADMIN);
    }

    @Test
    @DisplayName("H1：C 端令牌访问 /admin/** 必须 403")
    void userTokenOnAdminPath_forbidden() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/order/list").header("Authorization", "tok").build());
        when(jwtTool.parseToken("tok")).thenReturn(userToken());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("H1：管理端令牌访问 /app/** 必须 403")
    void adminTokenOnAppPath_forbidden() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/app/order/list").header("Authorization", "tok").build());
        when(jwtTool.parseToken("tok")).thenReturn(adminToken());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("H1：令牌无效或过期必须 401")
    void invalidToken_unauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/app/order/list").header("Authorization", "bad").build());
        when(jwtTool.parseToken("bad")).thenThrow(new UnauthorizedException("token已经过期"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("H1：角色匹配的令牌放行，并由网关权威注入 user-info")
    void validUserToken_passesAndInjectsUserInfo() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/app/order/list")
                        .header("Authorization", "Bearer tok")
                        .header("user-info", "999")
                        .build());
        when(jwtTool.parseToken("tok")).thenReturn(userToken());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        // 外部自带的 user-info 被覆盖为令牌中的真实身份
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("user-info")).isEqualTo("7");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("H1/H3：白名单路径也必须剥离外部自带的 user-info 与 X-Internal-Token")
    void excludePath_stripsForgedIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/app/auth/login")
                        .header("user-info", "999")
                        .header("X-Internal-Token", "secret")
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.get("user-info")).isNull();
        assertThat(headers.get("X-Internal-Token")).isNull();
        verify(jwtTool, never()).parseToken(anyString());
    }

    @Test
    @DisplayName("H2：支付回调精确路径免登录，其余 /app/pay/** 仍需鉴权")
    void notifyWhitelist_isExactNotWildcard() {
        MockServerWebExchange allowed = MockServerWebExchange.from(
                MockServerHttpRequest.post("/app/pay/notify/wechat").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(allowed, chain).block();

        verify(chain).filter(any());

        MockServerWebExchange blocked = MockServerWebExchange.from(
                MockServerHttpRequest.post("/app/pay/notify/other").build());
        when(jwtTool.parseToken(null)).thenThrow(new UnauthorizedException("未登录"));

        filter.filter(blocked, chain).block();

        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

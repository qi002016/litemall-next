package com.litemall.filter;


import com.litemall.config.AuthProperties;
import com.litemall.config.InnerApiProperties;
import com.litemall.domain.TokenPrincipal;
import com.litemall.exception.ForbiddenException;
import com.litemall.exception.UnauthorizedException;
import com.litemall.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 网关注入给下游的身份头（H1：外部请求不得自带） */
    private static final String USER_INFO_HEADER = "user-info";

    private final AuthProperties authProperties;
    private final JwtTool jwtTool;
    private final AntPathMatcher antPathMatcher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1.获取请求
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();
        //2.剥离外部自带的身份/内部密钥头（H1/H3：防止绕过网关鉴权直接伪造 user-info 或 X-Internal-Token）
        ServerHttpRequest.Builder cleaner = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_INFO_HEADER);
                    headers.remove(InnerApiProperties.HEADER);
                });
        //3.判断是否需要做登录拦截
        if (isExclude(path)) {
            return chain.filter(exchange.mutate().request(cleaner.build()).build());
        }
        //4.获取token
        String token = null;
        List<String> headers = request.getHeaders().get("Authorization");
        if (headers != null && !headers.isEmpty()) {
            token = headers.get(0);
            // 兼容 "Bearer xxx" 形式，去掉 scheme 前缀
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        //5.校验并解析token，并按路径前缀校验角色/受众（H1）
        TokenPrincipal principal;
        try {
            principal = jwtTool.parseToken(token);
            checkRole(path, principal);
        } catch (UnauthorizedException e) {
            return complete(exchange.getResponse(), HttpStatus.UNAUTHORIZED);
        } catch (ForbiddenException e) {
            return complete(exchange.getResponse(), HttpStatus.FORBIDDEN);
        } catch (Exception e) {
            return complete(exchange.getResponse(), HttpStatus.UNAUTHORIZED);
        }
        //6. 传递用户信息（由网关权威注入）
        ServerWebExchange swe = exchange.mutate()
                .request(cleaner.header(USER_INFO_HEADER, String.valueOf(principal.userId())).build())
                .build();
        return chain.filter(swe);
    }

    /**
     * H1：按 {@code hm.auth.role-rules} 校验路径要求的角色与受众。
     */
    private void checkRole(String path, TokenPrincipal principal) {
        List<AuthProperties.RoleRule> rules = authProperties.getRoleRules();
        if (rules == null) {
            return;
        }
        for (AuthProperties.RoleRule rule : rules) {
            if (!antPathMatcher.match(rule.getPattern(), path)) {
                continue;
            }
            if (rule.getRole() != null && !principal.hasRole(rule.getRole())) {
                throw new ForbiddenException("无权访问该接口");
            }
            if (rule.getAud() != null && !principal.hasAud(rule.getAud())) {
                throw new ForbiddenException("无权访问该接口");
            }
            return;
        }
    }

    private boolean isExclude(String path) {
        for (String pathPattern : authProperties.getExcludePaths()) {
            if (antPathMatcher.match(pathPattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> complete(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}

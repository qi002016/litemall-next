package com.litemall.filter;

import cn.hutool.core.util.StrUtil;
import com.litemall.domain.TokenPrincipal;
import com.litemall.jwt.JwtTool;
import com.litemall.utils.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * H1：管理端自身鉴权过滤链（纵深防御）。
 * <p>
 * 网关已按路径前缀做角色校验，但直连 8008 端口可绕过网关；
 * 本过滤器在 admin 侧再次要求 {@code Authorization} 携带 role=admin 的令牌，
 * 并以令牌为准填充 {@link UserContext}，不信任外部传入的 {@code user-info} 头。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {

    /** 免登录路径（与网关白名单一致） */
    private static final Set<String> ANONYMOUS_PATHS = Set.of("/admin/auth/login");

    private final JwtTool jwtTool;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        // 屏蔽外部自带的身份/内部密钥头：common 的 UserInfoInterceptor 在本过滤器之后执行，
        // 若不剥离，攻击者可带合法 token 同时伪造 user-info 头来冒用他人身份
        HttpServletRequest sanitized = new jakarta.servlet.http.HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (isTrustedOnlyHeader(name)) {
                    return null;
                }
                return super.getHeader(name);
            }

            @Override
            public java.util.Enumeration<String> getHeaders(String name) {
                if (isTrustedOnlyHeader(name)) {
                    return java.util.Collections.emptyEnumeration();
                }
                return super.getHeaders(name);
            }

            private boolean isTrustedOnlyHeader(String name) {
                return "user-info".equalsIgnoreCase(name)
                        || "X-Internal-Token".equalsIgnoreCase(name);
            }
        };
        if (ANONYMOUS_PATHS.contains(path)) {
            filterChain.doFilter(sanitized, response);
            return;
        }
        String token = resolveToken(request);
        try {
            TokenPrincipal principal = jwtTool.parseToken(token);
            if (!principal.hasRole(TokenPrincipal.ROLE_ADMIN) || !principal.hasAud(TokenPrincipal.AUD_ADMIN)) {
                reject(response, HttpStatus.FORBIDDEN, "无权访问管理端");
                return;
            }
            UserContext.setUser(principal.userId());
            filterChain.doFilter(sanitized, response);
        } catch (Exception e) {
            reject(response, HttpStatus.UNAUTHORIZED, "未登录或令牌无效");
        } finally {
            UserContext.removeUser();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isNotBlank(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return token;
    }

    private void reject(HttpServletResponse response, HttpStatus status, String msg) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status.value() + ",\"msg\":\"" + msg + "\",\"data\":null}");
    }
}

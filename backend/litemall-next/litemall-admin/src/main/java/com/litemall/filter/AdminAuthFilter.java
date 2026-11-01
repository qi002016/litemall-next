package com.litemall.filter;

import cn.hutool.core.util.StrUtil;
import com.litemall.domain.TokenPrincipal;
import com.litemall.jwt.JwtTool;
import com.litemall.support.AdminSessionVersion;
import com.litemall.support.AdminTokenIssuer;
import com.litemall.utils.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * </p>
 * <p>
 * P-1（05-R2 实施口径 §5.12）：令牌除签名/有效期/角色外还要校验会话版本
 * {@code ver} 声明与 Redis {@code auth:ver:{adminId}} 是否一致；禁用账号、重置口令后
 * 该键 {@code INCR}，旧令牌随即失效。兼容性：老令牌没有 {@code ver} 声明按 {@code 0}
 * 处理；Redis 不可读时<b>降级放行</b>（缓存故障不能造成全站 401），仅记 WARN。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {

    /** 免登录路径（与网关白名单一致） */
    private static final Set<String> ANONYMOUS_PATHS = Set.of("/admin/auth/login");

    /** 会话失效错误码（任务卡 CI-P-1 指定；§1.3 尚未登记，已在交付报告标注） */
    private static final String SESSION_EXPIRED = "SESSION_EXPIRED";

    private final JwtTool jwtTool;
    private final AdminSessionVersion sessionVersion;

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

        // 1) 令牌本身：签名 / 有效期 / 必要声明 / 角色与受众
        TokenPrincipal principal;
        try {
            principal = jwtTool.parseToken(token);
        } catch (Exception e) {
            reject(response, HttpStatus.UNAUTHORIZED, "未登录或令牌无效", null);
            return;
        }
        if (!principal.hasRole(TokenPrincipal.ROLE_ADMIN) || !principal.hasAud(TokenPrincipal.AUD_ADMIN)) {
            reject(response, HttpStatus.FORBIDDEN, "无权访问管理端", null);
            return;
        }

        // 2) 会话版本（P-1）：ver 声明 vs auth:ver:{adminId}；Redis 不可读 ⇒ 降级放行
        Integer currentVersion = sessionVersion.current(principal.userId());
        if (currentVersion != null) {
            Integer tokenVersion = AdminTokenIssuer.readVersion(token);
            int present = tokenVersion == null ? 0 : tokenVersion;
            if (present != currentVersion) {
                reject(response, HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录", SESSION_EXPIRED);
                return;
            }
        }

        // 3) 放行；异常处理交给统一异常处理链，不能被这里的 catch 误判成 401
        UserContext.setUser(principal.userId());
        try {
            filterChain.doFilter(sanitized, response);
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

    private void reject(HttpServletResponse response, HttpStatus status, String msg, String errorCode)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        String data = errorCode == null ? "null" : "{\"errorCode\":\"" + errorCode + "\"}";
        response.getWriter().write("{\"code\":" + status.value() + ",\"msg\":\"" + msg + "\",\"data\":" + data + "}");
    }
}
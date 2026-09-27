package com.litemall.config;

import com.litemall.domain.Admin;
import com.litemall.domain.AdminLog;
import com.litemall.mapper.AdminLogMapper;
import com.litemall.mapper.AdminMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/**
 * 记录管理端写操作（POST/PUT/DELETE）日志到 litemall_log
 */
@Component
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor {

    private final AdminLogMapper logMapper;
    private final AdminMapper adminMapper;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            return;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/admin/auth/login")) {
            return;
        }
        String userInfo = request.getHeader("user-info");
        String adminName = "system";
        if (userInfo != null) {
            try {
                Admin a = adminMapper.selectById(Long.valueOf(userInfo));
                if (a != null) adminName = a.getUsername();
            } catch (Exception ignored) {}
        }
        boolean ok = ex == null && response.getStatus() < 400;
        AdminLog log = new AdminLog();
        log.setAdmin(adminName);
        log.setIp(request.getRemoteAddr());
        log.setType(1);
        log.setAction(method + " " + path);
        log.setStatus(ok ? 1 : 0);
        log.setResult(ok ? "成功" : "失败(" + response.getStatus() + ")");
        log.setAddTime(LocalDateTime.now());
        log.setUpdateTime(LocalDateTime.now());
        log.setDeleted(false);
        logMapper.insert(log);
    }
}

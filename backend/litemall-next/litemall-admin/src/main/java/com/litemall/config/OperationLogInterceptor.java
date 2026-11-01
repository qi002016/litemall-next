package com.litemall.config;

import com.litemall.domain.AdminAccount;
import com.litemall.domain.AdminOperationLog;
import com.litemall.mapper.AdminAccountMapper;
import com.litemall.mapper.AdminOperationLogMapper;
import com.litemall.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/**
 * 记录管理端写操作（POST/PUT/DELETE）日志到 litemall_log（admin 域自有表，H5 后
 * 原 {@code AdminLog}/{@code Admin} 实体已分别替换为 {@link AdminOperationLog}/{@link AdminAccount}）。
 * <p>
 * 操作人取自 {@code AdminAuthFilter} 写入的 {@link UserContext}（令牌中的 adminId），
 * 而非可伪造的 {@code user-info} 请求头——该头在过滤器里已被剥离。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor {

    private final AdminOperationLogMapper logMapper;
    private final AdminAccountMapper adminAccountMapper;

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
        String adminName = "system";
        try {
            Long adminId = UserContext.getUser();
            if (adminId != null) {
                AdminAccount a = adminAccountMapper.selectById(adminId);
                if (a != null) adminName = a.getUsername();
            }
        } catch (Exception ignored) {
            // H5：日志记录失败不影响业务响应
        }
        boolean ok = ex == null && response.getStatus() < 400;
        AdminOperationLog log = new AdminOperationLog();
        log.setAdmin(adminName);
        log.setIp(request.getRemoteAddr());
        log.setType(1);
        log.setAction(method + " " + path);
        log.setStatus(ok ? 1 : 0);
        log.setResult(ok ? "成功" : "失败(" + response.getStatus() + ")");
        // BR-ADM-03：控制器的业务补充信息（备注 / 操作对象）合并进同一行，避免重复日志
        OperationLogContext.Extra extra = OperationLogContext.current();
        if (extra != null) {
            log.setComment(extra.comment());
            log.setTargetType(extra.targetType());
            log.setTargetId(extra.targetId());
        }
        log.setAddTime(LocalDateTime.now());
        log.setUpdateTime(LocalDateTime.now());
        log.setDeleted(false);
        logMapper.insert(log);
    }
}
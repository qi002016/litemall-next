package com.litemall.config;

import com.litemall.domain.AdminAccount;
import com.litemall.domain.AdminOperationLog;
import com.litemall.mapper.AdminAccountMapper;
import com.litemall.mapper.AdminOperationLogMapper;
import com.litemall.support.LogErrorCounter;
import com.litemall.support.LogTargetTypes;
import com.litemall.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/**
 * <p>
 * 记录管理端写操作（POST/PUT/DELETE）日志到 {@code litemall_log}（admin 域自有表）。
 * </p>
 * <p>
 * R2 收口（05-R2 实施口径 §2.5.3 / §5.11 / D-36）：
 * </p>
 * <ul>
 *   <li><b>字典</b>：{@code status} 0=成功 / 1=失败（R1 旧实现写的是 1=成功，R2 已翻转）；
 *       {@code type} 1=业务操作（登录 {@code type=2} 由认证入口另写）；</li>
 *   <li><b>target_type 必填且必须在 §2.5.1 白名单内</b>：控制器经 {@link OperationLogContext} 声明的值优先，
 *       否则按路径推导（{@link LogTargetTypes#resolve}），杜绝「筛不出来的审计缺口」；</li>
 *   <li><b>绝不影响业务</b>：取账号、写库全过程兜底；写库失败只 WARN +
 *       {@link LogErrorCounter#increase()}（{@code admin:logerr:{yyyyMMdd}}，CI-08 展示），不抛异常。</li>
 * </ul>
 * <p>
 * 操作人取自 {@code AdminAuthFilter} 写入的 {@link UserContext}（令牌中的 adminId），
 * 而非可伪造的 {@code user-info} 请求头——该头在过滤器里已被剥离。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor {

    private final AdminOperationLogMapper logMapper;
    private final AdminAccountMapper adminAccountMapper;
    private final LogErrorCounter logErrorCounter;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            record(request, response, ex);
        } catch (Exception e) {
            // 最后一道兜底：日志逻辑的任何意外都不能冒泡到业务响应
            log.warn("操作日志记录失败（已忽略）：{}", e.toString());
            logErrorCounter.increase();
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            return;
        }
        String path = request.getRequestURI();
        if (path == null || path.startsWith("/admin/auth/login")) {
            // 登录成功/失败由认证入口写 type=2 日志（登录失败同样是 HTTP 200，此处按状态码判不出来）
            return;
        }

        // 对外端点恒 HTTP 200（03 §4.1），业务失败只能由 LogFailureMarker 打出的请求属性判定
        boolean markedFailed = Boolean.TRUE.equals(request.getAttribute(LogFailureMarker.ATTR_FAILED));
        boolean ok = !markedFailed && ex == null && response.getStatus() < 400;
        OperationLogContext.Extra extra = OperationLogContext.current();

        AdminOperationLog entry = new AdminOperationLog();
        entry.setAdmin(resolveAdminName());
        entry.setIp(request.getRemoteAddr());
        entry.setType(1);
        // R2（05 §3.14）：内容停用类动作的 action 用业务文案（「停用公告」/「启用公告」），
        // 未声明时回退默认的 METHOD path
        entry.setAction(extra != null && extra.action() != null && !extra.action().isBlank()
                ? extra.action() : method + " " + path);
        // D-36 冻结字典：status 0=成功 1=失败
        entry.setStatus(ok ? 0 : 1);
        entry.setResult(ok ? "成功" : "失败(" + failureReason(request, response) + ")");
        // BR-ADM-03：控制器的业务补充信息（备注 / 操作对象）合并进同一行，避免重复日志
        entry.setComment(extra == null ? null : extra.comment());
        entry.setTargetType(LogTargetTypes.resolve(path, extra == null ? null : extra.targetType()));
        entry.setTargetId(resolveTargetId(path, extra));
        entry.setAddTime(LocalDateTime.now());
        entry.setUpdateTime(LocalDateTime.now());
        entry.setDeleted(false);

        try {
            logMapper.insert(entry);
        } catch (Exception e) {
            // §5.11：写失败不得阻断业务，只 WARN + 计数（工作台「日志异常」）
            log.warn("操作日志写入失败（不影响业务）：action={}, cause={}",
                    entry.getAction(), e.toString());
            logErrorCounter.increase();
        }
    }

    /**
     * 操作人用户名：令牌 adminId → 账号名；取不到时落 {@code system}（R1 既有行为）。
     */
    private String resolveAdminName() {
        try {
            Long adminId = UserContext.getUser();
            if (adminId != null) {
                AdminAccount account = adminAccountMapper.selectById(adminId);
                if (account != null && account.getUsername() != null) {
                    return account.getUsername();
                }
            }
        } catch (Exception e) {
            log.debug("操作人解析失败，按 system 记录：{}", e.toString());
        }
        return "system";
    }

    /**
     * 失败原因摘要：优先细分码（{@code RESOURCE_NOT_FOUND}），其次 {@code Result.code}，最后 HTTP 状态码。
     */
    static String failureReason(HttpServletRequest request, HttpServletResponse response) {
        Object errorCode = request.getAttribute(LogFailureMarker.ATTR_ERROR_CODE);
        if (errorCode != null) {
            return String.valueOf(errorCode);
        }
        Object code = request.getAttribute(LogFailureMarker.ATTR_CODE);
        if (code != null) {
            return String.valueOf(code);
        }
        return String.valueOf(response.getStatus());
    }

    /**
     * {@code target_id}：控制器声明值优先；否则取路径末段数字（如
     * {@code DELETE /admin/brand/12}）；都没有则落 {@code 0} 作为「未声明具体对象」哨兵
     * （{@code litemall_log.id} 自增从 1 起，0 不会与真实记录冲突）。
     */
    static Long resolveTargetId(String path, OperationLogContext.Extra extra) {
        if (extra != null && extra.targetId() != null) {
            return extra.targetId();
        }
        if (path != null) {
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash < path.length() - 1) {
                String tail = path.substring(slash + 1);
                if (!tail.isEmpty() && tail.chars().allMatch(Character::isDigit) && tail.length() <= 18) {
                    try {
                        return Long.valueOf(tail);
                    } catch (NumberFormatException ignored) {
                        // 超长数字串：按未声明处理
                    }
                }
            }
        }
        return 0L;
    }
}
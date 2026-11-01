package com.litemall.support;

import com.litemall.domain.AdminOperationLog;
import com.litemall.mapper.AdminOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * <p>
 * 登录日志写入（D-36：{@code type=2} 登录，1 业务操作）。
 * </p>
 * <p>
 * 为什么必须由登录入口自己写：登录失败对外同样是 HTTP 200（03 §4.1），
 * {@code OperationLogInterceptor} 只看状态码判不出成功/失败，因此登录路径在拦截器里被跳过、
 * 由本类按 {@code Result.code} 语义显式记录（成功 {@code status=0}，失败 {@code status=1}）。
 * </p>
 * <p>
 * 写入失败不阻断登录：只 WARN + {@link LogErrorCounter#increase()}（§5.11）。
 * </p>
 *
 * @since 2026-11-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminLoginLogger {

    /** {@code litemall_log.admin} 列宽（varchar(45)） */
    private static final int ADMIN_MAX = 45;
    /** {@code litemall_log.ip} 列宽（varchar(45)） */
    private static final int IP_MAX = 45;
    /** {@code litemall_log.result} 列宽（varchar(127)） */
    private static final int RESULT_MAX = 127;

    /** 未登录成功时 {@code target_id} 的哨兵值（与拦截器一致，真实主键从 1 起） */
    private static final long NO_TARGET = 0L;

    private final AdminOperationLogMapper logMapper;
    private final LogErrorCounter logErrorCounter;

    /**
     * 记录一次登录尝试。
     *
     * @param username 尝试登录的账号名（失败时也要记录，用于审计撞库）
     * @param ip       客户端 IP
     * @param adminId  命中的账号 ID（用户名不存在时传 {@code null}）
     * @param ok       是否登录成功
     * @param result   结果文案（成功「成功」；失败「失败(LOGIN_FAILED)」等）
     */
    public void record(String username, String ip, Long adminId, boolean ok, String result) {
        try {
            AdminOperationLog entry = new AdminOperationLog();
            entry.setAdmin(truncate(username == null ? "unknown" : username, ADMIN_MAX));
            entry.setIp(truncate(ip, IP_MAX));
            entry.setType(2);
            entry.setAction("登录");
            // D-36 冻结字典：0=成功 1=失败
            entry.setStatus(ok ? 0 : 1);
            entry.setResult(truncate(result, RESULT_MAX));
            // §2.5.1 白名单内的「管理账号」类型
            entry.setTargetType("admin");
            entry.setTargetId(adminId == null ? NO_TARGET : adminId);
            entry.setAddTime(LocalDateTime.now());
            entry.setUpdateTime(LocalDateTime.now());
            entry.setDeleted(false);
            logMapper.insert(entry);
        } catch (Exception e) {
            log.warn("登录日志写入失败（不影响登录）：user={}, cause={}", username, e.toString());
            logErrorCounter.increase();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
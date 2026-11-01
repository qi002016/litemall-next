package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litemall.domain.AdminAccount;
import com.litemall.domain.DTO.AdminLoginDTO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AdminLoginVO;
import com.litemall.domain.VO.AdminStatusVO;
import com.litemall.exception.CommonException;
import com.litemall.mapper.AdminAccountMapper;
import com.litemall.support.AdminLoginLogger;
import com.litemall.support.AdminSessionVersion;
import com.litemall.support.AdminTokenIssuer;
import com.litemall.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 管理端登录/身份接口。
 * </p>
 * <p>
 * H5：账号数据来自 admin 域自有表 {@code litemall_admin}（原 {@code Admin} 实体已删除，
 * 由 {@link AdminAccount} 承载），登录响应的 JSON 字段与改造前完全一致。
 * </p>
 * <p>
 * R2 收口（05-R2 实施口径 §5.12 / §5.14 / P-1 / P-2）：
 * </p>
 * <ul>
 *   <li>密码错/用户名不存在 → 统一 {@code 401 LOGIN_FAILED}「用户名或密码错误」（防枚举）；</li>
 *   <li>{@code status=0} → {@code 403 ADMIN_DISABLED}「账号已被禁用」（禁用后不得登录）；</li>
 *   <li>签发令牌时把当前 {@code auth:ver:{adminId}} 写进 {@code ver} 声明，
 *       供 {@code AdminAuthFilter} 比对实现「禁用/改密后旧令牌立即失效」；</li>
 *   <li>登录成功/失败都写 {@code type=2} 日志（登录失败对外仍是 HTTP 200，只能由本入口判定）。</li>
 * </ul>
 *
 * @since 2026-09-27
 */
@Slf4j
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminAccountMapper adminAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminTokenIssuer tokenIssuer;
    private final AdminSessionVersion sessionVersion;
    private final AdminLoginLogger loginLogger;

    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO body, HttpServletRequest request) {
        String ip = request == null ? null : request.getRemoteAddr();
        AdminAccount admin = adminAccountMapper.selectOne(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getUsername, body.getUsername())
                .eq(AdminAccount::getDeleted, false));

        // 1. 认证：用户名不存在与密码错误必须同码同文案（防枚举）
        if (admin == null || !passwordEncoder.matches(body.getPassword(), admin.getPassword())) {
            loginLogger.record(body.getUsername(), ip, admin == null ? null : admin.getId(),
                    false, "失败(LOGIN_FAILED)");
            return Result.error(new CommonException("用户名或密码错误", 401, "LOGIN_FAILED"));
        }

        // 2. 账号状态闸门（P-2）：认证通过后才提示禁用，避免拿登录接口探测账号状态
        if (admin.getStatus() != null && admin.getStatus() == AdminStatusVO.DISABLED) {
            loginLogger.record(admin.getUsername(), ip, admin.getId(), false, "失败(ADMIN_DISABLED)");
            return Result.error(new CommonException("账号已被禁用", 403, "ADMIN_DISABLED"));
        }

        // 3. 会话版本：缺失视为 0；Redis 不可用时降级为 0（登录不能被缓存故障阻断），
        //    过滤器侧对「版本未知」同样是放行，二者一致，不会出现「刚登录就被踢」
        Integer version = sessionVersion.current(admin.getId());
        if (version == null) {
            log.warn("会话版本不可读，登录按 ver=0 降级签发（adminId={}）", admin.getId());
            version = 0;
        }
        String token = tokenIssuer.issue(admin.getId(), version);

        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(token);
        vo.setId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setAvatar(admin.getAvatar());
        loginLogger.record(admin.getUsername(), ip, admin.getId(), true, "成功");
        return Result.ok(vo);
    }

    @GetMapping("/info")
    public Result<AdminAccount> info() {
        Long adminId = UserContext.getUser();
        AdminAccount admin = adminAccountMapper.selectById(adminId);
        if (admin != null) {
            admin.setPassword(null);
            admin.setStatusText(AdminStatusVO.text(admin.getStatus()));
        }
        return Result.ok(admin);
    }
}
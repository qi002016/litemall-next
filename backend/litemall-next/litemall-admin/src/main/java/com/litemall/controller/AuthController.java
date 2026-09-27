package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litemall.domain.AdminAccount;
import com.litemall.domain.Result;
import com.litemall.domain.TokenPrincipal;
import com.litemall.jwt.JwtProperties;
import com.litemall.jwt.JwtTool;
import com.litemall.mapper.AdminAccountMapper;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 管理端登录/身份接口。
 * </p>
 * <p>
 * H5：账号数据来自 admin 域自有表 {@code litemall_admin}（原 {@code Admin} 实体已删除，
 * 由 {@link AdminAccount} 承载），登录响应的 JSON 字段与改造前完全一致。
 * </p>
 */
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminAccountMapper adminAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        AdminAccount admin = adminAccountMapper.selectOne(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getUsername, username)
                .eq(AdminAccount::getDeleted, false));
        if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
            return Result.error("账号或密码错误");
        }
        String token = jwtTool.createToken(admin.getId(), TokenPrincipal.ROLE_ADMIN,
                TokenPrincipal.AUD_ADMIN, jwtProperties.getTokenTTL());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("id", admin.getId());
        data.put("username", admin.getUsername());
        data.put("avatar", admin.getAvatar());
        return Result.ok(data);
    }

    @GetMapping("/info")
    public Result<AdminAccount> info() {
        Long adminId = UserContext.getUser();
        AdminAccount admin = adminAccountMapper.selectById(adminId);
        if (admin != null) {
            admin.setPassword(null);
        }
        return Result.ok(admin);
    }
}
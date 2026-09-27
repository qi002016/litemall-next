package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litemall.domain.AdminAccount;
import com.litemall.domain.DTO.AdminLoginDTO;
import com.litemall.domain.Result;
import com.litemall.domain.TokenPrincipal;
import com.litemall.domain.VO.AdminLoginVO;
import com.litemall.jwt.JwtProperties;
import com.litemall.jwt.JwtTool;
import com.litemall.mapper.AdminAccountMapper;
import com.litemall.utils.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO body) {
        AdminAccount admin = adminAccountMapper.selectOne(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getUsername, body.getUsername())
                .eq(AdminAccount::getDeleted, false));
        if (admin == null || !passwordEncoder.matches(body.getPassword(), admin.getPassword())) {
            return Result.error("账号或密码错误");
        }
        String token = jwtTool.createToken(admin.getId(), TokenPrincipal.ROLE_ADMIN,
                TokenPrincipal.AUD_ADMIN, jwtProperties.getTokenTTL());
        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(token);
        vo.setId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setAvatar(admin.getAvatar());
        return Result.ok(vo);
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
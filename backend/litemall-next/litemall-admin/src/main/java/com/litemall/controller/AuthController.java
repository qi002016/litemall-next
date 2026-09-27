package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litemall.config.JwtProperties;
import com.litemall.domain.Admin;
import com.litemall.domain.Result;
import com.litemall.domain.TokenPrincipal;
import com.litemall.mapper.AdminMapper;
import com.litemall.utils.JwtTool;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, username)
                .eq(Admin::getDeleted, false));
        if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
            return Result.error("账号或密码错误");
        }
        String token = jwtTool.createToken(admin.getId().longValue(), TokenPrincipal.ROLE_ADMIN,
                TokenPrincipal.AUD_ADMIN, jwtProperties.getTokenTTL());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("id", admin.getId());
        data.put("username", admin.getUsername());
        data.put("avatar", admin.getAvatar());
        return Result.ok(data);
    }

    @GetMapping("/info")
    public Result<Admin> info() {
        Long adminId = UserContext.getUser();
        Admin admin = adminMapper.selectById(adminId);
        if (admin != null) {
            admin.setPassword(null);
        }
        return Result.ok(admin);
    }
}

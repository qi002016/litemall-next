package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.AdminAccount;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.mapper.AdminAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * <p>
 * 管理端账号管理接口（admin 域自有表 litemall_admin；H5 后原 {@code Admin} 实体已删除）。
 * </p>
 */
@RestController
@RequestMapping("/admin/account")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAccountMapper adminAccountMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    public Result<PageVO<AdminAccount>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username) {
        Page<AdminAccount> p = adminAccountMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminAccount>()
                        .eq(AdminAccount::getDeleted, false)
                        .like(StringUtils.hasText(username), AdminAccount::getUsername, username)
                        .orderByDesc(AdminAccount::getId));
        p.getRecords().forEach(a -> a.setPassword(null));
        return Result.ok(PageVO.of(p));
    }

    @PostMapping("/create")
    public Result<Void> create(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String raw = body.get("password");
        if (!StringUtils.hasText(username) || !StringUtils.hasText(raw)) {
            return Result.error("账号和密码不能为空");
        }
        AdminAccount exists = adminAccountMapper.selectOne(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getUsername, username).eq(AdminAccount::getDeleted, false));
        if (exists != null) return Result.error("账号已存在");
        AdminAccount a = new AdminAccount();
        a.setUsername(username);
        a.setPassword(passwordEncoder.encode(raw));
        a.setAvatar(body.get("avatar"));
        a.setAddTime(LocalDateTime.now());
        a.setUpdateTime(LocalDateTime.now());
        a.setDeleted(false);
        adminAccountMapper.insert(a);
        return Result.ok();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody AdminAccount a) {
        a.setPassword(null);
        a.setUpdateTime(LocalDateTime.now());
        adminAccountMapper.updateById(a);
        return Result.ok();
    }

    @PutMapping("/reset-password")
    public Result<Void> resetPwd(@RequestBody Map<String, Object> body) {
        Long id = asLong(body.get("id"));
        String raw = (String) body.get("password");
        AdminAccount a = new AdminAccount();
        a.setId(id);
        a.setPassword(passwordEncoder.encode(raw));
        a.setUpdateTime(LocalDateTime.now());
        adminAccountMapper.updateById(a);
        return Result.ok();
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        AdminAccount a = new AdminAccount();
        a.setId(id);
        a.setDeleted(true);
        a.setUpdateTime(LocalDateTime.now());
        adminAccountMapper.updateById(a);
        return Result.ok();
    }

    /** 请求体中的 ID：JSON 整数字面量既可能是 Integer 也可能是 Long，统一按 64 位解析 */
    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
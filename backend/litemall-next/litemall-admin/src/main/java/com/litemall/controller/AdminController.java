package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.Admin;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/admin/account")
@RequiredArgsConstructor
public class AdminController {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    public Result<PageVO<Admin>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username) {
        Page<Admin> p = adminMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Admin>()
                        .eq(Admin::getDeleted, false)
                        .like(StringUtils.hasText(username), Admin::getUsername, username)
                        .orderByDesc(Admin::getId));
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
        Admin exists = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, username).eq(Admin::getDeleted, false));
        if (exists != null) return Result.error("账号已存在");
        Admin a = new Admin();
        a.setUsername(username);
        a.setPassword(passwordEncoder.encode(raw));
        a.setAvatar(body.get("avatar"));
        a.setAddTime(LocalDateTime.now());
        a.setUpdateTime(LocalDateTime.now());
        a.setDeleted(false);
        adminMapper.insert(a);
        return Result.ok();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody Admin a) {
        a.setPassword(null);
        a.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(a);
        return Result.ok();
    }

    @PutMapping("/reset-password")
    public Result<Void> resetPwd(@RequestBody Map<String, Object> body) {
        Integer id = (Integer) body.get("id");
        String raw = (String) body.get("password");
        Admin a = new Admin();
        a.setId(id);
        a.setPassword(passwordEncoder.encode(raw));
        a.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(a);
        return Result.ok();
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam Integer id) {
        Admin a = new Admin();
        a.setId(id);
        a.setDeleted(true);
        a.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(a);
        return Result.ok();
    }
}

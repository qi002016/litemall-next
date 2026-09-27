package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.AdminUser;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class UserController {

    private final AdminUserMapper userMapper;

    @GetMapping("/list")
    public Result<PageVO<AdminUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username) {
        Page<AdminUser> p = userMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getDeleted, false)
                        .like(StringUtils.hasText(username), AdminUser::getUsername, username)
                        .orderByDesc(AdminUser::getId));
        return Result.ok(PageVO.of(p));
    }

    @PostMapping("/status")
    public Result<Void> status(@RequestBody Map<String, Object> body) {
        Integer id = (Integer) body.get("id");
        Integer status = (Integer) body.get("status");
        AdminUser u = new AdminUser();
        u.setId(id);
        u.setStatus(status);
        userMapper.updateById(u);
        return Result.ok();
    }
}

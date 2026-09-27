package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.AdminLog;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.mapper.AdminLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/log")
@RequiredArgsConstructor
public class LogController {

    private final AdminLogMapper logMapper;

    @GetMapping("/list")
    public Result<PageVO<AdminLog>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String admin) {
        Page<AdminLog> p = logMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminLog>()
                        .eq(AdminLog::getDeleted, false)
                        .like(StringUtils.hasText(admin), AdminLog::getAdmin, admin)
                        .orderByDesc(AdminLog::getId));
        return Result.ok(PageVO.of(p));
    }
}

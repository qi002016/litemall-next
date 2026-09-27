package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.AdminOperationLog;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.mapper.AdminOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 管理端操作日志查询接口（admin 域自有表 litemall_log；H5 后原 {@code AdminLog} 实体已删除）。
 * </p>
 */
@RestController
@RequestMapping("/admin/log")
@RequiredArgsConstructor
public class LogController {

    private final AdminOperationLogMapper logMapper;

    @GetMapping("/list")
    public Result<PageVO<AdminOperationLog>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String admin) {
        Page<AdminOperationLog> p = logMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminOperationLog>()
                        .eq(AdminOperationLog::getDeleted, false)
                        .like(StringUtils.hasText(admin), AdminOperationLog::getAdmin, admin)
                        .orderByDesc(AdminOperationLog::getId));
        return Result.ok(PageVO.of(p));
    }
}
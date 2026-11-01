package com.litemall.controller;

import com.litemall.domain.dto.AutoCloseResultDTO;
import com.litemall.service.IAftersaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 售后定时任务入口（CN-IN-03）：待寄回超 7 天自动关闭。
 * </p>
 * <p>
 * 由调度（每分钟）或运维手工触发，内部接口无鉴权上下文；逐单 CAS，无命中返回
 * {@code {"closed":0,"failed":[]}}（不是 404）。批上限 200。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Slf4j
@RestController
@RequestMapping("/inner/aftersale")
@RequiredArgsConstructor
@Tag(name = "售后任务接口", description = "售后域定时任务入口")
public class AftersaleJobController {

    private final IAftersaleService aftersaleService;

    @Operation(summary = "CN-IN-03 待寄回超时自动关闭")
    @PostMapping("/auto-close")
    public AutoCloseResultDTO autoClose() {
        AutoCloseResultDTO result = aftersaleService.autoClose();
        log.info("[R1-03] 售后超时自动关闭完成：closed={}, failed={}", result.getClosed(),
                result.getFailed() == null ? 0 : result.getFailed().size());
        return result;
    }
}
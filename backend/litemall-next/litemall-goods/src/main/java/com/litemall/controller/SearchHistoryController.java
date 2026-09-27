package com.litemall.controller;

import com.litemall.domain.Result;
import com.litemall.service.ISearchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 搜索历史 前端控制器
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@RestController
@RequestMapping("/app/search")
@RequiredArgsConstructor
@Tag(name = "搜索", description = "搜索历史相关接口")
public class SearchHistoryController {

    private final ISearchHistoryService searchHistoryService;

    @Operation(summary = "获取搜索历史")
    @GetMapping("/history")
    public Result<List<String>> getSearchHistory() {
        return Result.ok(searchHistoryService.getSearchHistory());
    }

    @Operation(summary = "清空搜索历史")
    @PostMapping("/clear")
    public Result<Void> clearSearchHistory() {
        searchHistoryService.clearSearchHistory();
        return Result.ok();
    }

    @Operation(summary = "记录搜索关键词")
    @PostMapping("/record")
    public Result<Void> recordSearch(@RequestBody Map<String, String> body) {
        searchHistoryService.recordSearch(body.get("keyword"));
        return Result.ok();
    }
}

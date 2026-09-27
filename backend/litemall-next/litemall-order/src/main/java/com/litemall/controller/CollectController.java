package com.litemall.controller;

import com.litemall.domain.DTO.CollectAddDTO;
import com.litemall.domain.Result;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CollectVO;
import com.litemall.service.ICollectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/app/collect")
@Tag(name = "collect", description = "收藏相关接口")
@RequiredArgsConstructor
public class CollectController {

    private final ICollectService collectService;

    @Operation(summary = "我的收藏")
    @GetMapping("/list")
    public Result<PageVO<CollectVO>> list(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                          @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(collectService.listCollect(pageNum, pageSize));
    }

    @Operation(summary = "收藏/取消收藏")
    @PostMapping("/add")
    public Result<Map<String, Boolean>> add(@Validated @RequestBody CollectAddDTO dto) {
        boolean collected = collectService.addCollect(dto);
        return Result.ok(Map.of("collected", collected));
    }
}

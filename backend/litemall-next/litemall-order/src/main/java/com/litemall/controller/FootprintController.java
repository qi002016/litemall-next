package com.litemall.controller;

import com.litemall.domain.Result;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.FootprintVO;
import com.litemall.service.IFootprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/app/footprint")
@Tag(name = "footprint", description = "足迹相关接口")
@RequiredArgsConstructor
public class FootprintController {

    private final IFootprintService footprintService;

    @Operation(summary = "我的足迹")
    @GetMapping("/list")
    public Result<PageVO<FootprintVO>> list(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(footprintService.listFootprint(pageNum, pageSize));
    }

    @Operation(summary = "记录浏览足迹（浏览商品详情时调用）")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Map<String, Long> body) {
        footprintService.addFootprint(body.get("goodsId"));
        return Result.ok(null);
    }
}

package com.litemall.controller;

import com.litemall.domain.DTO.AftersaleApplyDTO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AftersaleApplyVO;
import com.litemall.service.IAftersaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app/aftersale")
@Tag(name = "aftersale", description = "售后相关接口")
@RequiredArgsConstructor
public class AftersaleController {

    private final IAftersaleService aftersaleService;

    @Operation(summary = "申请售后")
    @PostMapping("/apply")
    public Result<AftersaleApplyVO> apply(@Validated @RequestBody AftersaleApplyDTO dto) {
        return Result.ok(aftersaleService.applyAftersale(dto));
    }
}

package com.litemall.controller;

import com.litemall.domain.DTO.AftersaleApplyDTO;
import com.litemall.domain.DTO.AftersaleCancelDTO;
import com.litemall.domain.DTO.AftersaleReturnShipDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AftersaleApplyVO;
import com.litemall.domain.VO.AftersaleDetailVO;
import com.litemall.domain.VO.AftersaleListItemVO;
import com.litemall.domain.VO.AftersaleReturnShipVO;
import com.litemall.service.IAftersaleService;
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

/**
 * <p>
 * C 端售后接口（07 §4）：申请 / 列表 / 详情 / 撤销 / 填写退货物流。
 * </p>
 * <p>
 * 全部需要登录态（{@code UserContext} 由网关注入）；他人售后单统一 404，避免探测。
 * 金额一律整数分。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@RestController
@RequestMapping("/app/aftersale")
@Tag(name = "aftersale", description = "售后相关接口")
@RequiredArgsConstructor
public class AftersaleController {

    private final IAftersaleService aftersaleService;

    @Operation(summary = "CI-91 申请售后（按订单行）")
    @PostMapping("/apply")
    public Result<AftersaleApplyVO> apply(@Validated @RequestBody AftersaleApplyDTO dto) {
        return Result.ok(aftersaleService.apply(dto));
    }

    @Operation(summary = "CN-15 我的售后列表")
    @GetMapping("/list")
    public Result<PageVO<AftersaleListItemVO>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "isAsc", required = false) Boolean isAsc) {
        return Result.ok(aftersaleService.list(status, pageNo, pageSize, sortBy, isAsc));
    }

    @Operation(summary = "CN-16 售后详情")
    @GetMapping("/detail")
    public Result<AftersaleDetailVO> detail(@RequestParam("aftersaleId") Long aftersaleId) {
        return Result.ok(aftersaleService.detail(aftersaleId));
    }

    @Operation(summary = "CN-17 撤销售后申请")
    @PostMapping("/cancel")
    public Result<Void> cancel(@Validated @RequestBody AftersaleCancelDTO dto) {
        aftersaleService.cancel(dto.getAftersaleId());
        return Result.ok(null);
    }

    @Operation(summary = "CN-18 填写退货物流")
    @PostMapping("/return-ship")
    public Result<AftersaleReturnShipVO> returnShip(@Validated @RequestBody AftersaleReturnShipDTO dto) {
        return Result.ok(aftersaleService.returnShip(dto));
    }
}
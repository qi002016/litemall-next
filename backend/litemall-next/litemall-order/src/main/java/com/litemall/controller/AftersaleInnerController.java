package com.litemall.controller;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.AftersaleAdminDTO;
import com.litemall.domain.dto.AftersaleAdminDetailDTO;
import com.litemall.domain.dto.AftersaleApproveDTO;
import com.litemall.domain.dto.AftersaleApproveResultDTO;
import com.litemall.domain.dto.AftersaleCloseDTO;
import com.litemall.domain.dto.AftersaleCloseResultDTO;
import com.litemall.domain.dto.AftersaleIdDTO;
import com.litemall.domain.dto.AftersaleReceiveResultDTO;
import com.litemall.domain.dto.AftersaleRefundDTO;
import com.litemall.domain.dto.AftersaleRefundResultDTO;
import com.litemall.domain.dto.AftersaleRejectDTO;
import com.litemall.domain.dto.AftersaleRejectResultDTO;
import com.litemall.domain.dto.AftersaleSkipReceiveDTO;
import com.litemall.service.IAftersaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 售后域内部接口（供 admin 服务 Feign 调用，R1-05 对外转发的唯一入口）。
 * </p>
 * <p>
 * 契约（07 §6.2 / 03 §4.1）：<b>不加 {@code Result} 包装</b>，失败保留非 200 HTTP 状态
 * （404 {@code AFTERSALE_NOT_FOUND} / 409 {@code AFTERSALE_STATUS_ILLEGAL} / 400 {@code PARAM_INVALID} 等），
 * 错误码放在响应体 {@code data.errorCode}（{@code CommonException} 统一渲染）。
 * {@code adminId} 由 admin 侧显式透传（内部接口无用户上下文）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@RestController
@RequestMapping("/inner/order/admin/aftersale")
@RequiredArgsConstructor
@Tag(name = "售后内部接口", description = "供 admin 服务 Feign 调用的售后管理接口")
public class AftersaleInnerController {

    private final IAftersaleService aftersaleService;

    @Operation(summary = "CN-01 管理端售后分页列表")
    @GetMapping("/list")
    public PageVO<AftersaleAdminDTO> adminList(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) Integer type,
            @RequestParam(value = "aftersaleSn", required = false) String aftersaleSn,
            @RequestParam(value = "orderSn", required = false) String orderSn,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "applyTimeMin", required = false) String applyTimeMin,
            @RequestParam(value = "applyTimeMax", required = false) String applyTimeMax,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "isAsc", required = false) Boolean isAsc) {
        return aftersaleService.adminList(pageNo, pageSize, status, type, aftersaleSn, orderSn, keyword,
                applyTimeMin, applyTimeMax, sortBy, isAsc);
    }

    @Operation(summary = "CN-02 管理端售后详情")
    @GetMapping("/detail")
    public AftersaleAdminDetailDTO adminDetail(@RequestParam("aftersaleId") Long aftersaleId) {
        return aftersaleService.adminDetail(aftersaleId);
    }

    @Operation(summary = "CN-03 审核通过（仅退款 0→3 / 退货退款 0→1 + 7 天寄回截止）")
    @PostMapping("/approve")
    public AftersaleApproveResultDTO approve(@RequestBody AftersaleApproveDTO dto) {
        return aftersaleService.approve(dto);
    }

    @Operation(summary = "CN-04 驳回（0→6）")
    @PostMapping("/reject")
    public AftersaleRejectResultDTO reject(@RequestBody AftersaleRejectDTO dto) {
        return aftersaleService.reject(dto);
    }

    @Operation(summary = "CN-05 确认收到退货（2→3）")
    @PostMapping("/confirm-receive")
    public AftersaleReceiveResultDTO confirmReceive(@RequestBody AftersaleIdDTO dto) {
        return aftersaleService.confirmReceive(dto);
    }

    @Operation(summary = "CN-06 跳过收货（1/2→3，留日志）")
    @PostMapping("/skip-receive")
    public AftersaleReceiveResultDTO skipReceive(@RequestBody AftersaleSkipReceiveDTO dto) {
        return aftersaleService.skipReceive(dto);
    }

    @Operation(summary = "CN-07 售后退款（2/3 发起，两段式；回补失败 200 + restockPending）")
    @PostMapping("/refund")
    public AftersaleRefundResultDTO refund(@RequestBody AftersaleRefundDTO dto) {
        return aftersaleService.refund(dto);
    }

    @Operation(summary = "CN-08 重试回补（仅 refunding + restock_status=2）")
    @PostMapping("/retry-restock")
    public AftersaleRefundResultDTO retryRestock(@RequestBody AftersaleIdDTO dto) {
        return aftersaleService.retryRestock(dto);
    }

    @Operation(summary = "CN-09 关闭售后（0/1/2→5）")
    @PostMapping("/close")
    public AftersaleCloseResultDTO close(@RequestBody AftersaleCloseDTO dto) {
        return aftersaleService.close(dto);
    }
}
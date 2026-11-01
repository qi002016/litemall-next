package com.litemall.controller;

import com.litemall.client.OrderClient;
import com.litemall.domain.DTO.AftersaleApproveReqDTO;
import com.litemall.domain.DTO.AftersaleCloseReqDTO;
import com.litemall.domain.DTO.AftersaleIdReqDTO;
import com.litemall.domain.DTO.AftersaleRefundReqDTO;
import com.litemall.domain.DTO.AftersaleRejectReqDTO;
import com.litemall.domain.DTO.AftersaleSkipReceiveReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
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
import com.litemall.exception.BadRequestException;
import com.litemall.support.AdminQuerySupport;
import com.litemall.utils.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 管理端售后接口（R1 范围：07 §4 的 CN-01 ~ CN-09）。
 * </p>
 * <p>
 * 分界（03 §6.2）：admin <b>只做转发 + 入参校验 + 身份透传</b>。售后的状态机
 * （8 态 CAS）、退款编排（资金 + 库存回补）、金额校验、退货期限计算，全部在 order 侧
 * {@code /inner/order/admin/aftersale/**} 完成；失败以非 200 + {@code data.errorCode}
 * 抛回，由 {@code AdminFeignErrorConfig} 还原成对应 HTTP 状态与业务码。
 * </p>
 * <p>
 * P-19：所有写操作显式透传当前 adminId（{@link UserContext#getUser()}），order 侧据此记审计，
 * 内部接口无用户上下文，缺失即无审计人。
 * </p>
 * <p>
 * P-25：退款时「资金已退、库存回补失败」是 <b>HTTP 200</b> + {@code restockPending=true}
 * （状态停 {@code refunding}），不是错误——本类不做任何失败改写。
 * </p>
 */
@RestController
@RequestMapping("/admin/aftersale")
@RequiredArgsConstructor
public class AftersaleController {

    /**
     * 07 §1.2 售后状态标识（8 态）+ 数字 0..7。
     * <p>
     * order 侧 {@code AftersaleStatus.parseMulti} 同时接受标识与数字；本类只做白名单拦截，
     * 避免明显错值打到下游。第 8 态在契约文档写 {@code canceled}、实际 ident 为
     * {@code cancelled}，两者都收并归一为 {@code cancelled}。
     * </p>
     */
    private static final Set<String> STATUS_TOKENS = Set.of(
            "applied", "waitReturn", "waitReceive", "refunding", "completed", "closed", "rejected",
            "canceled", "cancelled", "0", "1", "2", "3", "4", "5", "6", "7");

    /** 07 §4.1 售后类型：1 仅退款 / 2 退货退款（0 为历史存量，只读展示用） */
    private static final Set<Integer> TYPE_ALLOWED = Set.of(0, 1, 2);

    /** 07 §4.1 排序白名单（applyTime 默认 | amount） */
    private static final Set<String> SORT_BY = Set.of("applyTime", "amount");

    private final OrderClient orderClient;

    /**
     * CN-01 售后分页列表。
     *
     * @param pageNum      页码（≥1，默认 1；对外统一 pageNum，06 §3.1）
     * @param pageSize     每页条数（默认 20，上限 100）
     * @param status       售后状态，逗号多值（标识或 0..7）
     * @param type         售后类型 0/1/2
     * @param aftersaleSn  售后单号精确匹配
     * @param orderSn      订单号精确匹配
     * @param keyword      关键字（收货人/手机号/商品名，≤64）
     * @param applyTimeMin 申请时间下限 yyyy-MM-dd HH:mm:ss
     * @param applyTimeMax 申请时间上限 yyyy-MM-dd HH:mm:ss
     * @param sortBy       applyTime（默认）| amount
     * @param isAsc        是否升序（默认 true）
     * @return 分页售后行
     */
    @GetMapping("/list")
    public Result<PageVO<AftersaleAdminDTO>> list(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String aftersaleSn,
            @RequestParam(required = false) String orderSn,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String applyTimeMin,
            @RequestParam(required = false) String applyTimeMax,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Boolean isAsc) {
        return Result.ok(orderClient.adminAftersaleList(
                AdminQuerySupport.pageNum(pageNum),
                AdminQuerySupport.pageSize(pageSize),
                statusFilter(status),
                typeFilter(type),
                AdminQuerySupport.text(aftersaleSn, "aftersaleSn"),
                AdminQuerySupport.text(orderSn, "orderSn"),
                AdminQuerySupport.text(keyword, "keyword"),
                AdminQuerySupport.dateTime(applyTimeMin, "applyTimeMin"),
                AdminQuerySupport.dateTime(applyTimeMax, "applyTimeMax"),
                AdminQuerySupport.sortBy(sortBy, SORT_BY, "applyTime"),
                AdminQuerySupport.bool(isAsc, true)));
    }

    /**
     * CN-02 售后详情（售后单 + 订单摘要 + 订单行摘要 + 时间线）。
     *
     * @param aftersaleId 售后单ID
     * @return 详情
     */
    @GetMapping("/detail")
    public Result<AftersaleAdminDetailDTO> detail(@RequestParam Long aftersaleId) {
        return Result.ok(orderClient.adminAftersaleDetail(aftersaleId));
    }

    /**
     * CN-03 审核通过。
     * <p>
     * 退货退款（type=2）的退货地址：入参为空时由 order 侧读全局配置
     * {@code hm.aftersale.returnAddress}；入参非空仅覆盖本次响应、不落库（P-23）。
     * 配置与入参都为空 → order 侧 400 {@code PARAM_INVALID}，本类不预判。
     * </p>
     *
     * @param body 售后单ID + 退货地址（可空）+ 备注（可空）
     * @return 审核结果（含状态与退货期限）
     */
    @PostMapping("/approve")
    public Result<AftersaleApproveResultDTO> approve(@Valid @RequestBody AftersaleApproveReqDTO body) {
        AftersaleApproveDTO dto = new AftersaleApproveDTO();
        dto.setAftersaleId(body.getAftersaleId());
        dto.setReturnAddress(AdminQuerySupport.text(body.getReturnAddress(), 255, "returnAddress"));
        dto.setRemark(AdminQuerySupport.text(body.getRemark(), 255, "remark"));
        dto.setAdminId(UserContext.getUser());
        return Result.ok(orderClient.adminAftersaleApprove(dto));
    }

    /**
     * CN-04 驳回（仅 applied；原因必填）。
     *
     * @param body 售后单ID + 驳回原因
     * @return 驳回结果
     */
    @PostMapping("/reject")
    public Result<AftersaleRejectResultDTO> reject(@Valid @RequestBody AftersaleRejectReqDTO body) {
        AftersaleRejectDTO dto = new AftersaleRejectDTO();
        dto.setAftersaleId(body.getAftersaleId());
        dto.setReason(body.getReason());
        dto.setAdminId(UserContext.getUser());
        return Result.ok(orderClient.adminAftersaleReject(dto));
    }

    /**
     * CN-05 确认收到退货（仅 waitReceive 2 → refunding 3，不直接置已完成）。
     *
     * @param body 售后单ID
     * @return 收货结果（receiveTime）
     */
    @PostMapping("/confirm-receive")
    public Result<AftersaleReceiveResultDTO> confirmReceive(@Valid @RequestBody AftersaleIdReqDTO body) {
        return Result.ok(orderClient.adminAftersaleConfirmReceive(aftersaleIdDTO(body.getAftersaleId())));
    }

    /**
     * CN-06 跳过收货（waitReturn/waitReceive → refunding，原因必填，留日志）。
     *
     * @param body 售后单ID + 跳过原因
     * @return 收货结果（skippedReceive=true）
     */
    @PostMapping("/skip-receive")
    public Result<AftersaleReceiveResultDTO> skipReceive(@Valid @RequestBody AftersaleSkipReceiveReqDTO body) {
        AftersaleSkipReceiveDTO dto = new AftersaleSkipReceiveDTO();
        dto.setAftersaleId(body.getAftersaleId());
        dto.setReason(body.getReason());
        dto.setAdminId(UserContext.getUser());
        return Result.ok(orderClient.adminAftersaleSkipReceive(dto));
    }

    /**
     * CN-07 执行退款（退款编排与库存回补在同一下游事务段内）。
     * <p>
     * {@code amount} 只做一致性校验（须等于售后单金额，否则 400 {@code REFUND_AMOUNT_MISMATCH}），
     * 校验在下游；{@code null} 表示按售后单金额全额退。回补失败仍是 200 + restockPending（P-25）。
     * </p>
     *
     * @param body 售后单ID + 金额（可空）+ 备注（可空）
     * @return 退款结果
     */
    @PostMapping("/refund")
    public Result<AftersaleRefundResultDTO> refund(@Valid @RequestBody AftersaleRefundReqDTO body) {
        AftersaleRefundDTO dto = new AftersaleRefundDTO();
        dto.setAftersaleId(body.getAftersaleId());
        dto.setAmount(body.getAmount());
        dto.setRemark(AdminQuerySupport.text(body.getRemark(), 255, "remark"));
        dto.setAdminId(UserContext.getUser());
        return Result.ok(orderClient.adminAftersaleRefund(dto));
    }

    /**
     * CN-08 重试回补（仅 refunding 且 restockStatus=2，否则 409）。
     *
     * @param body 售后单ID
     * @return 与退款同结构的结果
     */
    @PostMapping("/retry-restock")
    public Result<AftersaleRefundResultDTO> retryRestock(@Valid @RequestBody AftersaleIdReqDTO body) {
        return Result.ok(orderClient.adminAftersaleRetryRestock(aftersaleIdDTO(body.getAftersaleId())));
    }

    /**
     * CN-09 关闭售后（applied/waitReturn/waitReceive → closed；退款中与终态 409）。
     *
     * @param body 售后单ID + 关闭原因
     * @return 关闭结果
     */
    @PostMapping("/close")
    public Result<AftersaleCloseResultDTO> close(@Valid @RequestBody AftersaleCloseReqDTO body) {
        AftersaleCloseDTO dto = new AftersaleCloseDTO();
        dto.setAftersaleId(body.getAftersaleId());
        dto.setReason(body.getReason());
        dto.setAdminId(UserContext.getUser());
        return Result.ok(orderClient.adminAftersaleClose(dto));
    }

    /** 售后单ID + 操作人（confirm-receive / retry-restock 共用） */
    private static AftersaleIdDTO aftersaleIdDTO(Long aftersaleId) {
        AftersaleIdDTO dto = new AftersaleIdDTO();
        dto.setAftersaleId(aftersaleId);
        dto.setAdminId(UserContext.getUser());
        return dto;
    }

    /**
     * 售后状态筛选：白名单校验 + 单 l 归一为 order 侧双 l 口径。
     *
     * @param raw 逗号多值（标识或 0..7，可空）
     * @return 归一后的逗号串；全空返回 null
     */
    private static String statusFilter(String raw) {
        String csv = AdminQuerySupport.csvTokens(raw, STATUS_TOKENS, "status");
        if (csv == null) {
            return null;
        }
        return Arrays.stream(csv.split(","))
                .map(token -> "canceled".equals(token) ? "cancelled" : token)
                .collect(Collectors.joining(","));
    }

    /** 售后类型白名单：只拦截明显越界值（0/1/2），其余语义在下游 */
    private static Integer typeFilter(Integer type) {
        if (type != null && !TYPE_ALLOWED.contains(type)) {
            throw new BadRequestException("type 取值非法：" + type, "PARAM_INVALID");
        }
        return type;
    }
}
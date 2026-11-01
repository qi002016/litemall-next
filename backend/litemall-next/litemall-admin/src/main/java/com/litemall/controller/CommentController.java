package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.config.OperationLogContext;
import com.litemall.domain.DTO.CommentReplyReqDTO;
import com.litemall.domain.DTO.CommentStatusReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.CommentAdminVO;
import com.litemall.domain.dto.CommentReplyDTO;
import com.litemall.domain.dto.CommentReplyResultDTO;
import com.litemall.domain.dto.CommentStatusDTO;
import com.litemall.domain.dto.CommentStatusResultDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.support.AdminQuerySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * <p>
 * 管理端评价接口（R2-04：CN-29 / CN-30 / CN-31）。
 * </p>
 * <p>
 * admin 只做入参归一（页码/每页条数/关键字长度/多值白名单/时间格式）与转发，
 * 「评价是否存在、状态能否切换、回复是否超长」等判定全部在 goods 侧
 * （05 §3.9–§3.11、03 §6.2 接口分界）。
 * </p>
 * <p>
 * 与 C 端的口径差异：本列表 {@code total} <b>含已隐藏评价</b>（运营要能看到全部数据），
 * 而 CI-35 商品评价列表只统计 {@code status=0}——两个 SQL 各自独立，不得复用。
 * </p>
 *
 * @since 2026-11-01
 */
@RestController
@RequestMapping("/admin/comment")
@RequiredArgsConstructor
@Tag(name = "管理端评价", description = "R2-04 评价列表、回复与隐藏/恢复")
public class CommentController {

    /** 操作日志对象类型（05 §4.2 冻结值；拦截器也能按路径推导，这里显式声明） */
    private static final String LOG_TARGET_TYPE = "comment";

    /** CN-29 星级白名单（1–5；逗号多值，非数值即 400） */
    private static final Set<String> STARS = Set.of("1", "2", "3", "4", "5");

    /** CN-29 评价状态白名单：0=显示、1=已隐藏 */
    private static final Set<String> STATUSES = Set.of("0", "1");

    private final GoodsClient goodsClient;

    /**
     * CN-29 评价分页列表（含已隐藏）。
     *
     * @param pageNum  页码（≥1，默认 1）
     * @param pageSize 每页条数（1–100，默认 20）
     * @param goodsId  商品ID（可空）
     * @param keyword  评价内容模糊匹配（≤50）
     * @param star     星级，逗号多值 1–5
     * @param replied  是否已回复（可空）
     * @param status   状态，逗号多值 0,1
     * @param dateFrom 起始时间（yyyy-MM-dd HH:mm:ss，含）
     * @param dateTo   截止时间（yyyy-MM-dd HH:mm:ss，含）
     * @return 分页评价行
     */
    @Operation(summary = "管理端评价分页列表")
    @GetMapping("/list")
    public Result<PageVO<CommentAdminVO>> list(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long goodsId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String star,
            @RequestParam(required = false) Boolean replied,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return Result.ok(goodsClient.adminCommentList(
                AdminQuerySupport.pageNum(pageNum),
                AdminQuerySupport.pageSize(pageSize),
                goodsId,
                AdminQuerySupport.text(keyword, 50, "keyword"),
                AdminQuerySupport.csvTokens(star, STARS, "star"),
                replied,
                AdminQuerySupport.csvTokens(status, STATUSES, "status"),
                AdminQuerySupport.dateTime(dateFrom, "dateFrom"),
                AdminQuerySupport.dateTime(dateTo, "dateTo")));
    }

    /**
     * CN-30 回复评价（覆盖写，{@code replyTime} 每次刷新）。
     *
     * @param body 评价ID + 回复内容（1–500 字）
     * @return {@code {commentId, adminContent, replyTime}}
     */
    @Operation(summary = "回复评价")
    @PostMapping("/reply")
    public Result<CommentReplyResultDTO> reply(@Valid @RequestBody CommentReplyReqDTO body) {
        String content = body.getContent() == null ? null : body.getContent().trim();
        if (content == null || content.isEmpty()) {
            // 05 §3.10：content 必填且「去空格非空」，纯空白等同未填
            throw new BadRequestException("回复内容不能为空", "PARAM_INVALID");
        }
        CommentReplyDTO dto = new CommentReplyDTO();
        dto.setCommentId(body.getCommentId());
        dto.setContent(content);
        CommentReplyResultDTO result = goodsClient.adminCommentReply(dto);
        OperationLogContext.mark("回复评价", LOG_TARGET_TYPE, body.getCommentId(), "回复评价");
        return Result.ok(result);
    }

    /**
     * CN-31 隐藏/恢复评价（{@code status} 为目标态，重复提交按幂等成功）。
     *
     * @param body 评价ID + 目标状态（0=显示、1=已隐藏）
     * @return {@code {commentId, status, statusText}}
     */
    @Operation(summary = "隐藏或恢复评价")
    @PostMapping("/status")
    public Result<CommentStatusResultDTO> status(@Valid @RequestBody CommentStatusReqDTO body) {
        CommentStatusDTO dto = new CommentStatusDTO();
        dto.setCommentId(body.getCommentId());
        dto.setStatus(body.getStatus());
        CommentStatusResultDTO result = goodsClient.adminCommentStatus(dto);
        OperationLogContext.mark(commentText(body.getStatus()), LOG_TARGET_TYPE, body.getCommentId(),
                commentText(body.getStatus()));
        return Result.ok(result);
    }

    /** 日志文案：隐藏/恢复（05 §3.11 冻结文案） */
    private static String commentText(Integer status) {
        return Integer.valueOf(1).equals(status) ? "隐藏评价" : "恢复评价";
    }
}
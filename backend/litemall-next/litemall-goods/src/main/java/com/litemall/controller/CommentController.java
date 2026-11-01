package com.litemall.controller;

import com.litemall.domain.DTO.CommentDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.CommentPostResultVO;
import com.litemall.domain.VO.CommentVO;
import com.litemall.exception.GoodsApiException;
import com.litemall.service.ICommentService;
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
 * 商品评价 前端控制器（C 端）
 * </p>
 * <p>
 * R2（05 §2.3 / §3.2）：
 * </p>
 * <ul>
 *   <li><b>CI-35</b> {@code GET /app/goods/comment/list}：新增 {@code star}/{@code hasPicture}
 *       筛选与分页归一，只返回未隐藏评价；</li>
 *   <li><b>CI-36</b> {@code POST /app/goods/comment/post}：改为锚订单行 + 返回 {@code commentId}
 *       与 {@code orderStatusUpdated}（R1 出参是 {@code Result<Void>}）。</li>
 * </ul>
 * <p>
 * 查询参数一律用 {@code String} 接收后手工解析（同 {@code GoodsController}）：Spring 的类型转换失败会抛
 * {@code MethodArgumentTypeMismatchException}，被全局 advice 转成 HTTP 4xx，而 C 端要求
 * <b>HTTP 恒 200</b> + {@code Result.code=400}。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@RestController
@RequestMapping("/app/goods/comment")
@RequiredArgsConstructor
@Tag(name = "商品评论", description = "商品评论相关接口")
public class CommentController {

    /** 分页缺省值（06 §3 评价列表与商品列表同口径） */
    private static final int DEFAULT_PAGE_NUM = 1;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final ICommentService commentService;

    @Operation(summary = "分页获取商品评论列表")
    @GetMapping("/list")
    public Result<PageVO<CommentVO>> getCommentList(
            @RequestParam(value = "goodsId", required = false) String goodsId,
            @RequestParam(value = "star", required = false) String star,
            @RequestParam(value = "hasPicture", required = false) String hasPicture,
            @RequestParam(value = "pageNum", required = false) String pageNum,
            @RequestParam(value = "pageSize", required = false) String pageSize) {
        Long id = parseLong(goodsId, "商品ID");
        if (id == null) {
            throw GoodsApiException.paramInvalid("商品ID不能为空");
        }
        Integer starValue = parseInt(star, "star");
        if (starValue != null && (starValue < 1 || starValue > 5)) {
            // 08 §3.2：star 取值 1–5，越界按参数非法（不是「查不到」）
            throw GoodsApiException.paramInvalid("star 取值范围为 1-5");
        }
        Integer num = parseInt(pageNum, "pageNum");
        Integer size = parseInt(pageSize, "pageSize");
        int normalizedNum = num == null || num < 1 ? DEFAULT_PAGE_NUM : num;
        int normalizedSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return Result.ok(commentService.getCommentList(id, starValue, parseBoolean(hasPicture, "hasPicture"),
                normalizedNum, normalizedSize));
    }

    @Operation(summary = "发表商品评论")
    @PostMapping("/post")
    public Result<CommentPostResultVO> addComment(@RequestBody @Validated CommentDTO dto) {
        return Result.ok(commentService.addComment(dto));
    }

    /* ==================== 手工参数解析 ==================== */

    private Integer parseInt(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw GoodsApiException.paramInvalid(name + " 必须是整数");
        }
    }

    private Long parseLong(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw GoodsApiException.paramInvalid(name + " 必须是数字");
        }
    }

    private Boolean parseBoolean(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return Boolean.FALSE;
        }
        throw GoodsApiException.paramInvalid(name + " 必须是布尔值");
    }
}
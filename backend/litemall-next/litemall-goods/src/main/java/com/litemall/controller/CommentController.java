package com.litemall.controller;

import com.litemall.domain.DTO.CommentDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.CommentVO;
import com.litemall.service.ICommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 评论表 前端控制器
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

    private final ICommentService commentService;

    @Operation(summary = "分页获取商品评论列表")
    @GetMapping("/list")
    public Result<PageVO<CommentVO>> getCommentList(Long goodsId, int pageNum, int pageSize) {
        return Result.ok(commentService.getCommentList(goodsId, pageNum, pageSize));
    }

    @Operation(summary = "发表商品评论")
    @PostMapping("/post")
    public Result<Void> addComment(@RequestBody @Validated CommentDTO dto) {
        commentService.addComment(dto);
        return Result.ok();
    }
}

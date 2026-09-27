package com.litemall.controller;

import com.litemall.domain.DTO.FeedbackSubmitDTO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.FeedbackVO;
import com.litemall.service.IFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 意见反馈接口
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@RestController
@RequestMapping("/app/feedback")
@RequiredArgsConstructor
@Tag(name = "feedback", description = "意见反馈接口")
public class FeedbackController {

    private final IFeedbackService feedbackService;

    @Operation(summary = "提交意见反馈")
    @PostMapping("/submit")
    public Result<FeedbackVO> submit(@RequestBody @Valid FeedbackSubmitDTO dto) {
        return Result.ok(feedbackService.submit(dto));
    }
}

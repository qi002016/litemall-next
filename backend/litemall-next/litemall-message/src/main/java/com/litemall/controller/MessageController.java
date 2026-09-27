package com.litemall.controller;

import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.MessageDetailVO;
import com.litemall.domain.VO.MessageVO;
import com.litemall.service.IMessageService;
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

/**
 * <p>
 * 消息服务接口
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@RestController
@RequestMapping("/app/msg")
@RequiredArgsConstructor
@Tag(name = "message", description = "消息相关接口")
public class MessageController {

    private final IMessageService messageService;

    @Operation(summary = "我的消息列表")
    @GetMapping("/list")
    public Result<PageVO<MessageVO>> list(@RequestParam(value = "type", required = false) String type,
                                          @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                          @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(messageService.listMessage(type, pageNum, pageSize));
    }

    @Operation(summary = "消息详情")
    @GetMapping("/detail")
    public Result<MessageDetailVO> detail(@RequestParam("id") Long id) {
        return Result.ok(messageService.getMessageDetail(id));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/read")
    public Result<Void> read(@RequestBody Map<String, Long> body) {
        messageService.markRead(body.get("id"));
        return Result.ok(null);
    }
}

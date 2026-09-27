package com.litemall.controller;

import com.litemall.service.IMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 消息服务内部接口（供 order/marketing 等服务 Feign 推送消息）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@RestController
@RequestMapping("/inner/msg")
@RequiredArgsConstructor
@Tag(name = "消息内部接口", description = "供服务间 Feign 调用的消息推送接口")
public class MessageInnerController {

    private final IMessageService messageService;

    @Operation(summary = "推送消息")
    @PostMapping("/push")
    public void push(@RequestParam("userId") Long userId,
                     @RequestParam(value = "type", defaultValue = "system") String type,
                     @RequestParam(value = "title", defaultValue = "") String title,
                     @RequestParam(value = "content", defaultValue = "") String content) {
        messageService.push(userId, type, title, content);
    }
}

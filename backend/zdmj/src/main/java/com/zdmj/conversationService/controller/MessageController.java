package com.zdmj.conversationService.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.zdmj.common.context.CurrentActor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.Result;
import jakarta.validation.Valid;

import java.util.concurrent.TimeUnit;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.dto.MessageResponse;
import com.zdmj.conversationService.service.MessageService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 消息控制器
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/messages")
@Validated
@Tag(name = "对话消息", description = "消息发送、流式回复与历史查询")
public class MessageController {

    private final MessageService messageService;

    /**
     * 分页查询会话消息列表（须为当前用户所属会话）
     *
     * @param actor 已认证操作者，包含用户编号
     * @param conversationId 会话ID（必填）
     * @param page           页码，默认 1
     * @param limit          每页条数，默认 20，最大 100
     */
    @GetMapping
    public Result<PageDTO<MessageResponse>> getMessagesByConversationId(@Parameter(hidden = true) CurrentActor actor,
            @RequestParam Long conversationId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return Result.success("查询消息列表成功",
                messageService.getMessagesByConversationId(actor, conversationId, page, limit));
    }

    /**
     * 创建流式消息
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 流式对话请求
     * @return 流式消息
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 30, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Parameter(hidden = true) CurrentActor actor, @Valid @RequestBody ChatStreamRequest request) {
        return messageService.createStream(actor, request);
    }
}

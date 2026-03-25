package com.zdmj.conversationService.controller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import com.zdmj.common.Result;
import com.zdmj.common.model.PageResult;
import com.zdmj.conversationService.dto.MessageDTO;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.service.MessageService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 消息控制器
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;

    /**
     * 分页查询会话消息列表（须为当前用户所属会话）
     *
     * @param conversationId 会话ID（必填）
     * @param page           页码，默认 1
     * @param limit          每页条数，默认 20，最大 100
     */
    @GetMapping
    public Result<PageResult<Message>> getMessagesByConversationId(
            @RequestParam Long conversationId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return Result.success("查询消息列表成功",
                messageService.getMessagesByConversationId(conversationId, page, limit));
    }

    /**
     * 创建流式消息
     * 
     * @param dto 消息DTO
     * @return 流式消息
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody MessageDTO dto) {
        return messageService.createStream(dto);
    }

    /**
     * 恢复流式消息
     * 
     * @param streamId 流式消息ID
     * @param offset   偏移量
     * @return 流式消息
     */
    @PostMapping(value = "/chat/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatResume(@RequestParam Long streamId,
            @RequestParam(defaultValue = "0") int offset) {
        return messageService.resumeStream(streamId, offset);
    }
}

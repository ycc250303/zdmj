package com.zdmj.conversationService.controller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import com.zdmj.conversationService.dto.MessageDTO;
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
     * @param offset 偏移量
     * @return 流式消息
     */
    @PostMapping(value = "/chat/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatResume(@RequestParam Long streamId,
            @RequestParam(defaultValue = "0") int offset) {
        return messageService.resumeStream(streamId, offset);
    }
}

package com.zdmj.conversationService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.common.model.PageDTO;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.dto.MessageResponse;
import com.zdmj.conversationService.entity.Message;

import reactor.core.publisher.Flux;

import org.springframework.http.codec.ServerSentEvent;

/**
 * 消息 Service
 */
public interface MessageService extends IService<Message> {

    /**
     * 根据会话ID分页查询消息列表（按 sequence 升序）
     */
    PageDTO<MessageResponse> getMessagesByConversationId(Long conversationId, Integer page, Integer limit);

    /**
     * 创建流式消息
     */
    Flux<ServerSentEvent<String>> createStream(ChatStreamRequest request);
}

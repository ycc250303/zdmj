package com.zdmj.conversationService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.model.PageDTO;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.dto.MessageResponse;

import reactor.core.publisher.Flux;

import org.springframework.http.codec.ServerSentEvent;

/**
 * 消息 Service
 */
public interface MessageService {

    /**
     * 根据会话ID分页查询消息列表（按 sequence 升序）
     *
     * @param actor 已认证操作者，包含用户编号
     */
    PageDTO<MessageResponse> getMessagesByConversationId(CurrentActor actor, Long conversationId, Integer page, Integer limit);

    /**
     * 创建流式消息
     *
     * @param actor 已认证操作者，包含用户编号
     */
    Flux<ServerSentEvent<String>> createStream(CurrentActor actor, ChatStreamRequest request);
}

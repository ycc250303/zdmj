package com.zdmj.conversationService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.conversationService.dto.MessageDTO;
import com.zdmj.conversationService.entity.Message;

import reactor.core.publisher.Flux;

import java.util.List;

import org.springframework.http.codec.ServerSentEvent;

/**
 * 消息 Service 骨架
 */
public interface MessageService extends IService<Message> {

    /**
     * 根据会话ID查询消息列表
     * 
     * @param conversationId 会话ID
     * @return 消息列表
     */
    List<Message> getByConversationId(Long conversationId);

    /**
     * 创建流式消息
     * 
     * @param dto 消息DTO
     * @return 流式消息
     */
    Flux<ServerSentEvent<String>> createStream(MessageDTO dto);

    /**
     * 恢复流式消息
     * 
     * @param streamId 流式消息ID
     * @param offset 偏移量
     * @return 流式消息
     */
    Flux<ServerSentEvent<String>> resumeStream(Long streamId, int offset);
}

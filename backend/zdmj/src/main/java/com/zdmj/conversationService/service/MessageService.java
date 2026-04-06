package com.zdmj.conversationService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.common.model.PageResult;
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
     * 根据会话ID分页查询消息列表（按 sequence 升序）
     *
     * @param conversationId 会话ID
     * @param page           页码，从 1 开始
     * @param limit          每页条数
     */
    PageResult<Message> getMessagesByConversationId(Long conversationId, Integer page, Integer limit);

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
     * @param offset   偏移量
     * @return 流式消息
     */
    Flux<ServerSentEvent<String>> resumeStream(Long streamId, int offset);
}

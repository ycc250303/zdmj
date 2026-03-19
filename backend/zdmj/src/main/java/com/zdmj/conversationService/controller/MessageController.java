package com.zdmj.conversationService.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.zdmj.common.Result;
import com.zdmj.common.context.UserHolder;
import com.zdmj.conversationService.entity.Messages;
import com.zdmj.conversationService.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 消息控制器（开发中）
 */
@Slf4j
@RestController
@RequestMapping("/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 创建消息（发送消息并获取AI回复，流式输出）
     *
     * @param conversationId 会话ID
     * @param requestBody    请求体，包含 content 字段
     * @return SSE流式事件
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> createMessage(@PathVariable Long conversationId,
            @RequestBody Map<String, String> requestBody) {
        // 在返回 Flux 之前完成授权检查，确保 SecurityContext 有效
        // 这样即使 Flux 在异步执行，授权检查也已经完成
        Long userId = UserHolder.requireUserId();
        log.debug("创建消息: conversationId={}, userId={}", conversationId, userId);
        String content = requestBody != null ? requestBody.get("content") : null;
        return messageService.createMessage(content, conversationId);
    }

    /**
     * 获取会话的消息列表（分页）
     *
     * @param conversationId 会话ID
     * @param page           页码（从1开始，默认为1）
     * @param size           每页数量（默认为20）
     * @return 消息列表
     */
    @GetMapping
    public Result<List<Messages>> getMessages(@PathVariable Long conversationId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success("查询消息列表成功",
                messageService.getMessagesByConversationId(conversationId, page, size));
    }

    /**
     * 根据ID获取消息详情
     *
     * @param conversationId 会话ID（用于验证消息是否属于该会话）
     * @param messageId      消息ID
     * @return 消息详情
     */
    @GetMapping("/{messageId}")
    public Result<Messages> getMessageById(@PathVariable Long conversationId,
            @PathVariable Long messageId) {
        Messages message = messageService.getById(messageId);
        // 验证消息是否属于指定的会话（增强安全性）
        if (message != null && !message.getConversationId().equals(conversationId)) {
            return Result.error("消息不属于指定的会话");
        }
        return Result.success("查询消息成功", message);
    }

    /**
     * 获取消息流（支持断点续传）
     *
     * @param conversationId 会话ID（用于验证消息是否属于该会话）
     * @param messageId      消息ID
     * @param recover        是否恢复之前的流式输出
     * @return SSE流式事件
     */
    @GetMapping(value = "/{messageId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> getMessageStream(
            @PathVariable Long conversationId,
            @PathVariable Long messageId,
            @RequestParam(required = false, defaultValue = "false") boolean recover) {
        // 先验证消息是否属于指定的会话，然后再获取流
        Messages message = messageService.getById(messageId);
        if (message != null && !message.getConversationId().equals(conversationId)) {
            return Flux.error(new com.zdmj.common.exception.BusinessException(
                    com.zdmj.common.exception.ErrorCode.NO_PERMISSION.getCode(), "消息不属于指定的会话"));
        }
        // 传递到Service层获取流（Service层会验证userId）
        return messageService.getStreamWithRecover(messageId, recover);
    }

    /**
     * 删除消息
     *
     * @param messageId 消息ID
     * @return 操作结果
     */
    @DeleteMapping("/{messageId}")
    public Result<String> deleteMessage(@PathVariable Long messageId) {
        messageService.delete(messageId);
        return Result.success("删除消息成功", null);
    }

    /**
     * 编辑消息并重新发送（流式输出）
     *
     * @param conversationId 会话ID
     * @param messageId      消息ID
     * @param newContent     新的消息内容
     * @return SSE流式事件
     */
    @PutMapping(value = "/{messageId}/edit-resend", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> editAndResend(@PathVariable Long conversationId,
            @PathVariable Long messageId,
            @RequestParam String newContent) {
        // 在返回 Flux 之前完成授权检查，确保 SecurityContext 有效
        Long userId = UserHolder.requireUserId();
        log.debug("编辑重发消息: conversationId={}, messageId={}, userId={}", conversationId, messageId, userId);
        return messageService.editAndResend(messageId, conversationId, newContent);
    }

    // /**
    // * 引用消息（待实现）
    // *
    // * @param conversationId 会话ID
    // * @param messageId 被引用的消息ID
    // * @return 引用消息的ID
    // */
    // @PostMapping("/{messageId}/quote")
    // public Result<Long> quoteMessage(@PathVariable Long conversationId,
    // @PathVariable Long messageId) {
    // return Result.success("引用消息成功", messageService.quote(messageId,
    // conversationId));
    // }
}

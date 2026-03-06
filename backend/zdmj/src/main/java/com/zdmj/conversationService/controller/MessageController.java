package com.zdmj.conversationService.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.Result;
import com.zdmj.conversationService.dto.MessagesDTO;
import com.zdmj.conversationService.entity.Messages;
import com.zdmj.conversationService.service.MessageService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息控制器
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
     * 创建消息（发送消息并获取AI回复）
     * 
     * @param conversationId 会话ID
     * @param messagesDTO    消息DTO
     * @return 创建的用户消息
     */
    @PostMapping
    public Result<Messages> createMessage(@PathVariable Long conversationId,
            @Valid @RequestBody MessagesDTO messagesDTO) {
        return Result.success("发送消息成功", messageService.createMessage(messagesDTO, conversationId));
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
     * @param messageId 消息ID
     * @return 消息详情
     */
    @GetMapping("/{messageId}")
    public Result<Messages> getMessageById(@PathVariable Long messageId) {
        return Result.success("查询消息成功", messageService.getById(messageId));
    }

    // /**
    // * 删除消息（待实现）
    // *
    // * @param messageId 消息ID
    // * @return 操作结果
    // */
    // @DeleteMapping("/{messageId}")
    // public Result<String> deleteMessage(@PathVariable Long messageId) {
    // messageService.delete(messageId);
    // return Result.success("删除消息成功", null);
    // }

    // /**
    // * 编辑消息并重新发送（待实现）
    // *
    // * @param messageId 消息ID
    // * @param newContent 新的消息内容
    // * @return 重新生成的消息
    // */
    // @PutMapping("/{messageId}/edit-resend")
    // public Result<Messages> editAndResend(@PathVariable Long messageId,
    // @RequestParam String newContent) {
    // return Result.success("编辑重发成功", messageService.editAndResend(messageId,
    // newContent));
    // }

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

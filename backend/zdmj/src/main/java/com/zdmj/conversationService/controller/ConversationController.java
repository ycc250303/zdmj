package com.zdmj.conversationService.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import com.zdmj.common.Result;
import com.zdmj.conversationService.dto.ConversationDTO;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.service.ConversationService;

import lombok.RequiredArgsConstructor;

/**
 * 对话控制器
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 创建会话
     * 
     * @param conversationDTO 会话DTO
     * @return 创建的会话
     */
    @PostMapping
    public Result<Conversation> createConversation(@RequestBody ConversationDTO conversationDTO) {
        Conversation conversation = conversationService.create(conversationDTO);
        return Result.success("会话创建成功", conversation);
    }

    /**
     * 根据ID查询会话
     * 
     * @param id 会话ID
     * @return 查询的会话
     */
    @GetMapping("/{id}")
    public Result<Conversation> getConversationById(@PathVariable Long id) {
        Conversation conversation = conversationService.getById(id);
        return Result.success("会话查询成功", conversation);
    }

    /**
     * 查询所有会话列表
     * 
     * @return 查询的会话列表
     */
    @GetMapping
    public Result<List<Conversation>> getConversations() {
        List<Conversation> conversations = conversationService.getByUserId();
        return Result.success("会话查询成功", conversations);
    }

    /**
     * 修改会话标题
     * 
     * @param id    会话ID
     * @param title 新会话标题
     * @return 更新后的会话
     */
    @PutMapping("/{id}/title")
    public Result<Conversation> updateTitle(@PathVariable Long id, @RequestParam("title") String title) {
        Conversation conversation = conversationService.updateTitle(id, title);
        return Result.success("会话标题修改成功", conversation);
    }

    /**
     * 删除会话
     * 
     * @param id 会话ID
     * @return 删除的会话
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteConversation(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.success("会话删除成功", null);
    }
}

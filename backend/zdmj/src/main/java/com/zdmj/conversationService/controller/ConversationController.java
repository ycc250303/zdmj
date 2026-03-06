package com.zdmj.conversationService.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.Result;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.conversationService.dto.ConversationsDTO;
import com.zdmj.conversationService.entity.Conversations;
import com.zdmj.conversationService.service.ConversationService;

import lombok.extern.slf4j.Slf4j;

/**
 * 会话控制器
 */
@Slf4j
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 创建会话
     * 
     * @param conversationDTO 会话DTO
     * @return 创建的会话
     */
    @PostMapping
    public Result<Conversations> createConversation(
            @Validated(CreateGroup.class) @RequestBody ConversationsDTO conversationDTO) {
        return Result.success("创建会话成功", conversationService.create(conversationDTO));
    }

    /**
     * 获取当前用户的会话列表
     * 
     * @return 会话列表
     */
    @GetMapping
    public Result<List<Conversations>> getConversations() {
        return Result.success("查询会话列表成功", conversationService.getByUserId());
    }

    /**
     * 根据ID获取会话详情
     * 
     * @param id 会话ID
     * @return 会话详情
     */
    @GetMapping("/{id}")
    public Result<Conversations> getConversationById(@PathVariable Long id) {
        return Result.success("查询会话成功", conversationService.getById(id));
    }

    /**
     * 删除会话
     * 
     * @param id 会话ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteConversation(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.success("删除会话成功", null);
    }

    /**
     * 置顶会话（待实现）
     * 
     * @param id 会话ID
     * @return 操作结果
     */
    @PostMapping("/{id}/pin")
    public Result<String> pinConversation(@PathVariable Long id) {
        conversationService.pin(id);
        return Result.success("置顶会话成功", null);
    }

    /**
     * 重命名会话（待实现）
     * 
     * @param id    会话ID
     * @param title 新标题
     * @return 操作结果
     */
    @PutMapping("/{id}/rename")
    public Result<String> renameConversation(@PathVariable Long id, @RequestParam String title) {
        conversationService.rename(id, title);
        return Result.success("重命名会话成功", null);
    }
}

package com.zdmj.conversationService.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import com.zdmj.common.model.Result;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.ConversationResponse;
import com.zdmj.conversationService.service.ConversationService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 对话控制器
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/conversations")
@Tag(name = "会话管理", description = "对话会话的创建、查询与删除")
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 创建会话
     *
     * @param request 会话请求
     * @return 创建的会话
     */
    @PostMapping
    public Result<ConversationResponse> createConversation(@RequestBody ConversationRequest request) {
        return Result.success("会话创建成功", conversationService.create(request));
    }

    /**
     * 根据ID查询会话
     *
     * @param id 会话ID
     * @return 查询的会话
     */
    @GetMapping("/{id}")
    public Result<ConversationResponse> getConversationById(@PathVariable Long id) {
        return Result.success("会话查询成功", conversationService.getById(id));
    }

    /**
     * 查询所有会话列表
     *
     * @return 查询的会话列表
     */
    @GetMapping
    public Result<List<ConversationResponse>> getConversations() {
        return Result.success("会话查询成功", conversationService.getByUserId());
    }

    /**
     * 修改会话标题
     *
     * @param id    会话ID
     * @param title 新会话标题
     * @return 更新后的会话
     */
    @PutMapping("/{id}/title")
    public Result<ConversationResponse> updateTitle(@PathVariable Long id, @RequestParam("title") String title) {
        return Result.success("会话标题修改成功", conversationService.updateTitle(id, title));
    }

    /**
     * 更新会话配置
     *
     * @param id     会话ID
     * @param config 配置片段
     * @return 更新后的会话
     */
    @PutMapping("/{id}/config")
    public Result<ConversationResponse> updateConfig(@PathVariable Long id, @RequestBody Map<String, Object> config) {
        return Result.success("会话配置更新成功", conversationService.updateConfig(id, config));
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

package com.zdmj.knowledgeService.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.model.Result;
import com.zdmj.knowledgeService.dto.KnowledgeBasesResponse;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/knowledge")
@Tag(name = "知识库", description = "个人知识库的创建与查询")
public class KnowledgeBasesController {

    private final KnowledgeBasesService knowledgeBasesService;

    /**
     * 创建知识库
     *
     * @return 知识库
     */
    @PostMapping
    public Result<KnowledgeBasesResponse> createKnowledgeBases() {
        return Result.success("创建知识库成功", knowledgeBasesService.create());
    }

    /**
     * 查询知识库
     *
     * @return 知识库
     */
    @GetMapping
    public Result<KnowledgeBasesResponse> getKnowledgeBases() {
        return Result.success("查询知识库成功", knowledgeBasesService.getByUserId());
    }

    /**
     * 清空知识库
     *
     * @return 删除结果
     */
    @DeleteMapping
    public Result<String> clearKnowledgeBases() {
        knowledgeBasesService.clear();
        return Result.success("清空知识库成功", null);
    }

}

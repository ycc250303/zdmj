package com.zdmj.knowledgeService.controller;

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
import com.zdmj.common.model.PageResult;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;
import com.zdmj.knowledgeService.dto.KnowledgeBasesDTO;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/knowledge")
public class KnowledgeBasesController {

    private final KnowledgeBasesService knowledgeBasesService;

    /**
     * 创建知识库
     * 
     * @param knowledgeBasesDTO 知识库DTO
     * @return 知识库
     */
    @PostMapping
    public Result<KnowledgeBases> createKnowledgeBases(
            @Validated(CreateGroup.class) @RequestBody KnowledgeBasesDTO knowledgeBasesDTO) {
        return Result.success("创建知识库成功", knowledgeBasesService.create(knowledgeBasesDTO));
    }

    /**
     * 分页查询知识库列表
     * 
     * @param page      页码，默认为1
     * @param limit     每页数量，默认为10
     * @param projectId 项目ID（可选）
     * @param type      知识类型（可选）
     * @return 分页结果
     */
    @GetMapping()
    public Result<PageResult<KnowledgeBases>> getKnowledgeBases(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer type) {
        return Result.success("查询知识库成功", knowledgeBasesService.getPage(page, limit, projectId, type));
    }

    /**
     * 根据ID查询知识库
     *
     * @param id 知识库ID
     * @return 知识库
     */
    @GetMapping("/{id}")
    public Result<KnowledgeBases> getKnowledgeBasesById(@PathVariable Long id) {
        return Result.success("查询知识库成功", knowledgeBasesService.getById(id));
    }

    /**
     * 更新知识库
     *
     * @param knowledgeBasesDTO 知识库DTO
     * @return 知识库
     */
    @PutMapping
    public Result<KnowledgeBases> updateKnowledgeBases(
            @Validated(UpdateGroup.class) @RequestBody KnowledgeBasesDTO knowledgeBasesDTO) {
        return Result.success("更新知识库成功", knowledgeBasesService.update(knowledgeBasesDTO));
    }

    /**
     * 删除知识库
     *
     * @param id 知识库ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteKnowledgeBases(@PathVariable Long id) {
        knowledgeBasesService.delete(id);
        return Result.success("删除知识库成功", null);
    }

}
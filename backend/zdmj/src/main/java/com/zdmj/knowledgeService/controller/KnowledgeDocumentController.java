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

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentDTO;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicDTO;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.service.KnowledgeDocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/knowledge-document")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 分页查询当前用户的知识文档
     *
     * @param page  页码，从 1 开始，默认 1
     * @param limit 每页条数，默认 20，最大 100
     */
    @GetMapping
    public Result<PageDTO<KnowledgeDocumentPublicDTO>> listKnowledgeDocuments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        return Result.success("查询知识文档列表成功", knowledgeDocumentService.getByPage(page, limit));
    }

    /**
     * 创建知识文档
     *
     * @param knowledgeDocumentDTO 知识文档DTO
     * @return 知识文档
     */
    @PostMapping
    public Result<KnowledgeDocument> createKnowledgeDocument(
            @Validated(CreateGroup.class) @RequestBody KnowledgeDocumentDTO knowledgeDocumentDTO) {
        return Result.success("创建知识文档成功", knowledgeDocumentService.create(knowledgeDocumentDTO));
    }

    /**
     * 根据ID获取知识文档
     *
     * @param id 知识文档ID
     * @return 知识文档
     */
    @GetMapping("/{id}")
    public Result<KnowledgeDocumentPublicDTO> getKnowledgeDocumentById(@PathVariable Long id) {
        return Result.success("查询知识文档成功", knowledgeDocumentService.getPublicById(id));
    }

    /**
     * 更新知识文档
     *
     * @param knowledgeDocumentDTO 知识文档DTO
     * @return 知识文档
     */
    @PutMapping
    public Result<KnowledgeDocument> updateKnowledgeDocument(
            @Validated(UpdateGroup.class) @RequestBody KnowledgeDocumentDTO knowledgeDocumentDTO) {
        return Result.success("更新知识文档成功", knowledgeDocumentService.update(knowledgeDocumentDTO));
    }

    /**
     * 删除知识文档
     *
     * @param id 知识文档ID
     * @return 知识文档
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledgeDocument(@PathVariable Long id) {
        knowledgeDocumentService.delete(id);
        return Result.success("删除知识文档成功", null);
    }
}

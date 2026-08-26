package com.zdmj.knowledgeService.service;

import com.zdmj.common.model.PageDTO;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentResponse;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;

public interface KnowledgeDocumentService {

    /**
     * 创建知识文档
     *
     * @param request 知识文档请求
     * @return 知识文档
     */
    KnowledgeDocumentResponse create(KnowledgeDocumentRequest request);

    /**
     * 根据ID获取知识文档（内部完整实体，含权限校验）
     *
     * @param id 知识文档ID
     * @return 知识文档实体
     */
    KnowledgeDocument getById(Long id);

    /**
     * 对外查询单条文档（脱敏字段）
     */
    KnowledgeDocumentPublicResponse getPublicById(Long id);

    /**
     * 分页查询当前用户知识文档（列表项为 {@link KnowledgeDocumentPublicResponse}）
     */
    PageDTO<KnowledgeDocumentPublicResponse> getByPage(Integer page, Integer limit);

    /**
     * 更新知识文档
     *
     * @param request 知识文档请求
     * @return 知识文档
     */
    KnowledgeDocumentResponse update(KnowledgeDocumentRequest request);

    /**
     * 删除知识文档
     *
     * @param id 知识文档ID
     */
    void delete(Long id);
}

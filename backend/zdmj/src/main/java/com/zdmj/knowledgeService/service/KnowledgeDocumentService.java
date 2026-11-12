package com.zdmj.knowledgeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.model.PageDTO;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentResponse;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;

public interface KnowledgeDocumentService {

    /**
     * 创建知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 知识文档请求
     * @return 知识文档
     */
    KnowledgeDocumentResponse create(CurrentActor actor, KnowledgeDocumentRequest request);

    /**
     * 根据ID获取知识文档（内部完整实体，含权限校验）
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 知识文档ID
     * @return 知识文档实体
     */
    KnowledgeDocument getById(CurrentActor actor, Long id);

    /**
     * 对外查询单条文档（脱敏字段）
     *
     * @param actor 已认证操作者，包含用户编号
     */
    KnowledgeDocumentPublicResponse getPublicById(CurrentActor actor, Long id);

    /**
     * 分页查询当前用户知识文档（列表项为 {@link KnowledgeDocumentPublicResponse}）
     *
     * @param actor 已认证操作者，包含用户编号
     */
    PageDTO<KnowledgeDocumentPublicResponse> getByPage(CurrentActor actor, Integer page, Integer limit);

    /**
     * 更新知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 知识文档请求
     * @return 知识文档
     */
    KnowledgeDocumentResponse update(CurrentActor actor, KnowledgeDocumentRequest request);

    /**
     * 删除知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 知识文档ID
     */
    void delete(CurrentActor actor, Long id);
}

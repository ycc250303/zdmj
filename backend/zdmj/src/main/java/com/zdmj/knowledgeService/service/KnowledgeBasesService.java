package com.zdmj.knowledgeService.service;

import com.zdmj.knowledgeService.dto.KnowledgeBasesResponse;

public interface KnowledgeBasesService {

    /**
     * 创建知识库的方法
     *
     * @return 创建后的知识库
     */
    KnowledgeBasesResponse create();

    /**
     * 根据用户ID获取知识库
     *
     * @return 用户关联的知识库
     */
    KnowledgeBasesResponse getByUserId();

    /**
     * 获取当前用户（scope=1）知识库主键；不存在则创建后返回。
     *
     * @return 知识库 id
     */
    Long getOrCreateKnowledgeBaseId();

    /**
     * 按 scope 查找知识库 ID；不存在时返回 {@code null}（不自动创建系统库）。
     *
     * @param scope {@link com.zdmj.knowledgeService.enums.KnowledgeScopeEnum#getCode()}
     */
    Long findKnowledgeBaseIdByScope(int scope);

    /**
     * 清空知识库
     */
    void clear();
}

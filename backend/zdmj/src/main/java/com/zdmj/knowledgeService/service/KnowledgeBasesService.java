package com.zdmj.knowledgeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.knowledgeService.dto.KnowledgeBasesResponse;

public interface KnowledgeBasesService {

    /**
     * 创建知识库的方法
     *
     * @param actor 已认证操作者，包含用户编号
     * @return 创建后的知识库
     */
    KnowledgeBasesResponse create(CurrentActor actor);

    /**
     * 根据用户ID获取知识库
     *
     * @param actor 已认证操作者，包含用户编号
     * @return 用户关联的知识库
     */
    KnowledgeBasesResponse getByUserId(CurrentActor actor);

    /**
     * 获取当前用户（scope=1）知识库主键；不存在则创建后返回。
     *
     * @param actor 已认证操作者，包含用户编号
     * @return 知识库 id
     */
    Long getOrCreateKnowledgeBaseId(CurrentActor actor);

    /**
     * 按 scope 查找知识库 ID；不存在时返回 {@code null}（不自动创建系统库）。
     *
     * @param scope {@link com.zdmj.knowledgeService.enums.KnowledgeScopeEnum#getCode()}
     */
    Long findKnowledgeBaseIdByScope(int scope);

    /**
     * 清空知识库
     *
     * @param actor 已认证操作者，包含用户编号
     */
    void clear(CurrentActor actor);
}

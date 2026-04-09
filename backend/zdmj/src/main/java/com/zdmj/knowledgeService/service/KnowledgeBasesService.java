package com.zdmj.knowledgeService.service;

import com.zdmj.knowledgeService.entity.KnowledgeBases;


public interface KnowledgeBasesService {

    /**
     * 创建知识库的方法
     *
     * @return KnowledgeBases 返回创建后的知识库实体
     */
    KnowledgeBases create();

    /**
     * 根据用户ID获取知识库列表
     *
     * @return 返回用户关联的知识库列表，KnowledgeBases类型的集合
     */
    KnowledgeBases getByUserId();

    /**
     * 获取当前用户（scope=1）知识库主键；不存在则创建后返回。
     *
     * @return 知识库 id
     */
    Long getOrCreateKnowledgeBaseId();

    /**
     * 更新知识库
     *
     * @return 返回更新后的知识库实体
     */
    // void update();

    /**
     * 清空知识库
     *
     */
    void clear();
}

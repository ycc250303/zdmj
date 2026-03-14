package com.zdmj.conversationService.service;

import com.zdmj.conversationService.dto.VectorRetrievalResult;

import java.util.List;

/**
 * 向量检索服务接口
 * 提供知识库向量和项目代码向量的检索功能
 */
public interface VectorRetrievalService {
        /**
         * 检索知识库向量
         *
         * @param queryText   查询文本
         * @param projectId   项目ID（可选，用于过滤）
         * @param knowledgeId 知识库ID（可选，用于过滤特定知识库）
         * @param topK        返回结果数量，默认10
         * @param minScore    最小相似度分数（0-1），默认0.0
         * @return 检索结果列表，按相似度降序排列
         */
        List<VectorRetrievalResult> retrieveKnowledgeVectors(
                        String queryText,
                        Long projectId,
                        Long knowledgeId,
                        Integer topK,
                        Double minScore);

        /**
         * 检索项目代码向量
         *
         * @param queryText 查询文本
         * @param projectId 项目ID（必填，用于过滤项目代码）
         * @param topK      返回结果数量，默认10
         * @param minScore  最小相似度分数（0-1），默认0.0
         * @return 检索结果列表，按相似度降序排列
         */
        List<VectorRetrievalResult> retrieveProjectCodeVectors(
                        String queryText,
                        Long projectId,
                        Integer topK,
                        Double minScore);
}

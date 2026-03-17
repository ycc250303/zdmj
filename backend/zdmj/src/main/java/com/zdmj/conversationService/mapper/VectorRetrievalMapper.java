package com.zdmj.conversationService.mapper;

import com.zdmj.conversationService.dto.VectorRetrievalResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 向量检索Mapper接口
 * 提供pgvector向量检索的SQL查询方法
 */
@Mapper
public interface VectorRetrievalMapper {
        /**
         * 检索知识库向量
         *
         * @param queryVector 查询向量（字符串格式：[0.1,0.2,...]）
         * @param userId      用户ID
         * @param projectId   项目ID（可选）
         * @param knowledgeId 知识库ID（可选）
         * @param topK        返回结果数量
         * @param minScore    最小相似度分数
         * @return 检索结果列表
         */
        List<VectorRetrievalResult> retrieveKnowledgeVectors(
                        @Param("queryVector") String queryVector,
                        @Param("userId") Long userId,
                        @Param("projectId") Long projectId,
                        @Param("knowledgeId") Long knowledgeId,
                        @Param("topK") Integer topK,
                        @Param("minScore") Double minScore);

        /**
         * 检索项目代码向量
         *
         * @param queryVector 查询向量（字符串格式：[0.1,0.2,...]）
         * @param userId      用户ID
         * @param projectId   项目ID（必填）
         * @param topK        返回结果数量
         * @param minScore    最小相似度分数
         * @return 检索结果列表
         */
        List<VectorRetrievalResult> retrieveProjectCodeVectors(
                        @Param("queryVector") String queryVector,
                        @Param("userId") Long userId,
                        @Param("projectId") Long projectId,
                        @Param("topK") Integer topK,
                        @Param("minScore") Double minScore);
}

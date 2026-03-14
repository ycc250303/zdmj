package com.zdmj.conversationService.service.impl;

import com.zdmj.common.context.UserHolder;
import com.zdmj.conversationService.dto.VectorRetrievalResult;
import com.zdmj.conversationService.mapper.VectorRetrievalMapper;
import com.zdmj.conversationService.service.VectorRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 向量检索服务实现类
 * 提供知识库向量和项目代码向量的检索功能
 */
@Slf4j
@Service
public class VectorRetrievalServiceImpl implements VectorRetrievalService {

    private final EmbeddingModel embeddingModel;
    private final VectorRetrievalMapper vectorRetrievalMapper;

    // 默认参数
    private static final int DEFAULT_TOP_K = 10;
    private static final double DEFAULT_MIN_SCORE = 0.0;

    public VectorRetrievalServiceImpl(EmbeddingModel embeddingModel, VectorRetrievalMapper vectorRetrievalMapper) {
        this.embeddingModel = embeddingModel;
        this.vectorRetrievalMapper = vectorRetrievalMapper;
    }

    @Override
    public List<VectorRetrievalResult> retrieveKnowledgeVectors(
            String queryText,
            Long projectId,
            Long knowledgeId,
            Integer topK,
            Double minScore) {
        try {
            // 参数校验
            if (queryText == null || queryText.trim().isEmpty()) {
                log.warn("查询文本为空，返回空结果");
                return Collections.emptyList();
            }
            Long userId = UserHolder.getUserId();
            if (userId == null) {
                log.warn("用户ID为空，返回空结果");
                return Collections.emptyList();
            }

            // 设置默认值
            int actualTopK = topK != null && topK > 0 ? topK : DEFAULT_TOP_K;
            double actualMinScore = minScore != null && minScore >= 0 ? minScore : DEFAULT_MIN_SCORE;

            // 生成查询向量
            String queryVector = generateQueryVector(queryText);
            if (queryVector == null) {
                log.error("生成查询向量失败，返回空结果");
                return Collections.emptyList();
            }

            // 执行检索
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"VectorRetrievalServiceImpl.java:%d\",\"message\":\"before retrieveKnowledgeVectors SQL\",\"data\":{\"projectId\":%d,\"userId\":%d,\"topK\":%d,\"minScore\":%f},\"runId\":\"run1\",\"hypothesisId\":\"G\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 67, projectId, userId, actualTopK,
                        actualMinScore));
                fw.close();
            } catch (Exception e) {
            }
            // #endregion
            List<VectorRetrievalResult> results;
            try {
                results = vectorRetrievalMapper.retrieveKnowledgeVectors(
                        queryVector, userId, projectId, knowledgeId, actualTopK, actualMinScore);
            } catch (Exception e) {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                    fw.write(String.format(
                            "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"VectorRetrievalServiceImpl.java:%d\",\"message\":\"retrieveKnowledgeVectors SQL exception\",\"data\":{\"projectId\":%d,\"error\":\"%s\"},\"runId\":\"run1\",\"hypothesisId\":\"G\"}\n",
                            System.currentTimeMillis(), System.currentTimeMillis(), 67, projectId, e.getMessage()));
                    fw.close();
                } catch (Exception e2) {
                }
                // #endregion
                log.error("检索知识库向量失败: userId={}, queryText={}", userId, queryText, e);
                return Collections.emptyList();
            }
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"VectorRetrievalServiceImpl.java:%d\",\"message\":\"after retrieveKnowledgeVectors SQL\",\"data\":{\"projectId\":%d,\"resultCount\":%d},\"runId\":\"run1\",\"hypothesisId\":\"G\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 68, projectId,
                        results != null ? results.size() : 0));
                fw.close();
            } catch (Exception e) {
            }
            // #endregion

            log.debug("知识库向量检索完成: userId={}, projectId={}, knowledgeId={}, topK={}, minScore={}, 结果数量={}",
                    userId, projectId, knowledgeId, actualTopK, actualMinScore, results.size());

            return results;
        } catch (Exception e) {
            Long userId = UserHolder.getUserId();
            log.error("检索知识库向量失败: userId={}, queryText={}", userId, queryText, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<VectorRetrievalResult> retrieveProjectCodeVectors(
            String queryText,
            Long projectId,
            Integer topK,
            Double minScore) {
        try {
            // 参数校验
            if (queryText == null || queryText.trim().isEmpty()) {
                log.warn("查询文本为空，返回空结果");
                return Collections.emptyList();
            }
            Long userId = UserHolder.getUserId();
            if (userId == null) {
                log.warn("用户ID为空，返回空结果");
                return Collections.emptyList();
            }
            if (projectId == null) {
                log.warn("项目ID为空，返回空结果");
                return Collections.emptyList();
            }

            // 设置默认值
            int actualTopK = topK != null && topK > 0 ? topK : DEFAULT_TOP_K;
            double actualMinScore = minScore != null && minScore >= 0 ? minScore : DEFAULT_MIN_SCORE;

            // 生成查询向量
            String queryVector = generateQueryVector(queryText);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"VectorRetrievalServiceImpl.java:%d\",\"message\":\"queryVector generated\",\"data\":{\"projectId\":%d,\"queryVectorIsNull\":%s,\"queryTextLength\":%d},\"runId\":\"run1\",\"hypothesisId\":\"B\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 108, projectId,
                        queryVector == null ? "true" : "false", queryText.length()));
                fw.close();
            } catch (Exception e) {
            }
            // #endregion
            if (queryVector == null) {
                log.error("生成查询向量失败，返回空结果");
                return Collections.emptyList();
            }

            // 执行检索
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"VectorRetrievalServiceImpl.java:%d\",\"message\":\"before SQL query\",\"data\":{\"projectId\":%d,\"userId\":%d,\"topK\":%d,\"minScore\":%f,\"queryVectorLength\":%d},\"runId\":\"run1\",\"hypothesisId\":\"D\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 115, projectId, userId, actualTopK,
                        actualMinScore, queryVector.length()));
                fw.close();
            } catch (Exception e) {
            }
            // #endregion
            List<VectorRetrievalResult> results;
            try {
                results = vectorRetrievalMapper.retrieveProjectCodeVectors(
                        queryVector, userId, projectId, actualTopK, actualMinScore);
            } catch (Exception e) {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                    fw.write(String.format(
                            "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"VectorRetrievalServiceImpl.java:%d\",\"message\":\"SQL query exception\",\"data\":{\"projectId\":%d,\"error\":\"%s\"},\"runId\":\"run1\",\"hypothesisId\":\"E\"}\n",
                            System.currentTimeMillis(), System.currentTimeMillis(), 137, projectId, e.getMessage()));
                    fw.close();
                } catch (Exception e2) {
                }
                // #endregion
                log.error("检索项目代码向量失败: userId={}, projectId={}, queryText={}", userId, projectId, queryText, e);
                return Collections.emptyList();
            }
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"VectorRetrievalServiceImpl.java:%d\",\"message\":\"after SQL query\",\"data\":{\"projectId\":%d,\"resultCount\":%d},\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 150, projectId,
                        results != null ? results.size() : 0));
                if (results != null && !results.isEmpty()) {
                    fw.write(String.format(
                            "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"VectorRetrievalServiceImpl.java:%d\",\"message\":\"first result sample\",\"data\":{\"projectId\":%d,\"firstResultContent\":\"%s\",\"firstResultScore\":%f},\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n",
                            System.currentTimeMillis(), System.currentTimeMillis(), 151, projectId,
                            results.get(0).getContent() != null
                                    ? results.get(0).getContent().substring(0,
                                            Math.min(100, results.get(0).getContent().length()))
                                    : "null",
                            results.get(0).getScore() != null ? results.get(0).getScore() : 0.0));
                }
                fw.close();
            } catch (Exception e) {
            }
            // #endregion

            log.debug("项目代码向量检索完成: userId={}, projectId={}, topK={}, minScore={}, 结果数量={}",
                    userId, projectId, actualTopK, actualMinScore, results.size());

            return results;
        } catch (Exception e) {
            Long userId = UserHolder.getUserId();
            log.error("检索项目代码向量失败: userId={}, projectId={}, queryText={}", userId, projectId, queryText, e);
            return Collections.emptyList();
        }
    }

    /**
     * 生成查询向量
     * 使用EmbeddingModel将查询文本转换为向量，然后格式化为PostgreSQL pgvector所需的字符串格式
     * 带完善的错误处理和降级策略
     *
     * @param queryText 查询文本
     * @return 向量字符串格式：[0.1,0.2,0.3,...]，失败返回null
     */
    private String generateQueryVector(String queryText) {
        if (queryText == null || queryText.trim().isEmpty()) {
            log.warn("查询文本为空，无法生成向量");
            return null;
        }

        try {
            // 调用EmbeddingModel生成向量
            EmbeddingRequest request = new EmbeddingRequest(List.of(queryText), null);
            EmbeddingResponse response = embeddingModel.call(request);

            // 验证响应
            if (response == null) {
                log.error("EmbeddingModel返回null响应");
                return null;
            }
            if (response.getResult() == null) {
                log.error("EmbeddingModel响应中result为null");
                return null;
            }
            if (response.getResult().getOutput() == null) {
                log.error("EmbeddingModel响应中output为null");
                return null;
            }

            // EmbeddingModel返回的是float[]数组，需要转换为字符串
            float[] embeddingArray = response.getResult().getOutput();
            if (embeddingArray == null || embeddingArray.length == 0) {
                log.error("生成的向量数组为空或长度为0");
                return null;
            }

            // 检查向量维度（应该是1024维，但允许其他维度）
            if (embeddingArray.length != 1024) {
                log.warn("向量维度不匹配: 实际{}维，期望1024维，将使用实际维度", embeddingArray.length);
            }

            // 转换为PostgreSQL pgvector所需的字符串格式：[0.1,0.2,0.3,...]
            StringBuilder vectorString = new StringBuilder("[");
            for (int i = 0; i < embeddingArray.length; i++) {
                if (i > 0) {
                    vectorString.append(",");
                }
                vectorString.append(embeddingArray[i]);
            }
            vectorString.append("]");

            log.debug("成功生成查询向量: queryText长度={}, 向量维度={}",
                    queryText.length(), embeddingArray.length);
            return vectorString.toString();

        } catch (RuntimeException e) {
            // EmbeddingClient运行时异常（包括API调用失败、网络异常等）
            String errorType = e.getClass().getSimpleName();
            log.error("EmbeddingClient调用失败: queryText={}, errorType={}, error={}",
                    queryText, errorType, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            // 其他未知异常
            log.error("生成查询向量时发生未知异常: queryText={}, errorType={}",
                    queryText, e.getClass().getSimpleName(), e);
            return null;
        }
    }
}

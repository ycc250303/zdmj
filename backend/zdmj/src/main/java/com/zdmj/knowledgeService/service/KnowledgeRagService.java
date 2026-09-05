package com.zdmj.knowledgeService.service;

import java.util.List;
import java.util.Map;

import com.zdmj.knowledgeService.dto.KnowledgeRetrievalResponse;

import reactor.core.publisher.Flux;

/**
 * 知识库两步 RAG：先向量检索，再将片段注入 system，最后走 ChatClient 流式输出。
 * 与对话层解耦：不负责写入 messages 表，只返回模型 token 流。
 */
public interface KnowledgeRagService {

    Flux<String> streamAnswer(Long userId, Long conversationId, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge, Map<String, Object> promptVars);

    /**
     * 排序层检索：空白归一、query 改写、双路 ANN、动态 topK、minScore；不做整篇展开。
     * 供评测与其它只需要命中列表的调用方复用，避免在脚本里复制检索策略。
     */
    KnowledgeRetrievalResponse retrieveRanked(Long userId, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge);
}

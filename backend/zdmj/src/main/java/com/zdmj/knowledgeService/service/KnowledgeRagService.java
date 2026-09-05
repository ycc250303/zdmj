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
     * 排序层检索：改写、每句一次 embed、双路 ANN、minScore、合并后截断 topK。
     * streamAnswer 直接调用本方法；两库都关时不改写、空 hits。
     */
    KnowledgeRetrievalResponse retrieveRanked(Long userId, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge);
}

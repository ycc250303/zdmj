package com.zdmj.knowledgeService.service;

import reactor.core.publisher.Flux;

/**
 * 知识库两步 RAG：先向量检索，再将片段注入 system，最后走 ChatClient 流式输出。
 * 与对话层解耦：不负责写入 messages 表，只返回模型 token 流。
 */
public interface KnowledgeRagService {

    Flux<String> streamAnswer(Long conversationId, String userMessage);
}
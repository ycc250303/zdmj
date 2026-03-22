package com.zdmj.conversationService.service;

/**
 * 项目RAG服务接口
 * 提供项目上下文检索功能，包括项目信息获取、多维度查询构建、检索结果格式化等
 */
public interface ProjectRAGService {
    /**
     * 检索项目上下文
     * 根据项目ID获取项目信息，然后检索相关的知识库文档和项目代码，并格式化为XML格式的上下文字符串
     *
     * @param projectId   项目ID
     * @param userId      用户ID
     * @param userMessage 用户消息（用于查询）
     * @return 格式化的项目上下文字符串（XML格式）
     */
    String retrieveProjectContext(Long projectId, Long userId, String userMessage);
}

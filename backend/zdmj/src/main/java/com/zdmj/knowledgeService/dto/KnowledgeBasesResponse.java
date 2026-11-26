package com.zdmj.knowledgeService.dto;

/**
 * 知识库响应（镜像当前 KnowledgeBases Entity JSON）
 *
 * @param id 知识库编号，对应 knowledge_bases.id
 * @param userId 所属用户编号
 * @param scope 知识库范围，对应 scope：1 用户私有、2 系统通用、3 学习路线
 */
public record KnowledgeBasesResponse(
        Long id,
        Long userId,
        Integer scope) {
}

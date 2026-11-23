package com.zdmj.knowledgeService.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
 * 知识库排序检索请求（不含整篇展开、不生成回答）。
 *
 * @param query 用户原始问句
 * @param ragDocumentIds 用户库文档范围：{@code null} 检索用户库全部；空列表不检索用户库。
 * @param useSystemKnowledge 是否检索系统知识库
 */
public record KnowledgeRetrievalRequest(
        @NotBlank(message = "查询内容不能为空")
        String query,
        List<Long> ragDocumentIds,
        boolean useSystemKnowledge) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public KnowledgeRetrievalRequest {
        ragDocumentIds = ragDocumentIds == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(ragDocumentIds));
    }
}

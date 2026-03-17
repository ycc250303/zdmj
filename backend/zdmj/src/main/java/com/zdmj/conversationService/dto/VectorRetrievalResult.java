package com.zdmj.conversationService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 向量检索结果DTO
 * 用于返回知识库向量和项目代码向量的检索结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorRetrievalResult {
    /**
     * 向量ID
     */
    private Long id;

    /**
     * 知识库ID（对于knowledge_vectors）或项目ID（对于project_code_vectors）
     */
    private Long knowledgeId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 内容（文档块内容或代码片段内容）
     */
    private String content;

    /**
     * 元数据（JSONB格式）
     */
    private Map<String, Object> metadata;

    /**
     * 文档块索引（仅knowledge_vectors有）
     */
    private Integer chunkIndex;

    /**
     * 文件路径（仅project_code_vectors有）
     */
    private String filePath;

    /**
     * 相似度分数（1 - 余弦距离，范围0-1，越大越相似）
     */
    private Double score;
}

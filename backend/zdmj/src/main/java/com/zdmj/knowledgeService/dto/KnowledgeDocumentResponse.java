package com.zdmj.knowledgeService.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识文档创建/更新响应（镜像当前 KnowledgeDocument Entity JSON）
 */
@Data
public class KnowledgeDocumentResponse {

    private Long id;

    private Long knowledgeId;

    private Long userId;

    private Integer type;

    private String content;

    private String title;

    private String contentHash;

    private Integer embeddingStatus;

    private Integer chunkCount;

    private LocalDateTime lastEmbeddedAt;

    private String lastError;

    private Map<String, Object> metadata;
}

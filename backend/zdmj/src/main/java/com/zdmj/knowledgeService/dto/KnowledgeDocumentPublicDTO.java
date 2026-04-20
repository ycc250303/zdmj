package com.zdmj.knowledgeService.dto;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

/**
 * 知识文档对外视图（单条 GET 与分页 {@code PageDTO#list} 元素结构一致；不含 knowledgeId、userId、contentHash、chunkCount）。
 */
@Data
public class KnowledgeDocumentPublicDTO {

    /**
     * 文档ID
     */
    private Long id;

    /**
     * 文档类型
     */
    private Integer type;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 文档标题
     */
    private String title;

    /** 向量化状态：PENDING、RUNNING、SUCCESS、FAILED；未知或未设置时为 null */
    private String embeddingStatus;

    /**
     * 最后向量化时间
     */
    private LocalDateTime lastEmbeddedAt;

    /**
     * 最后向量化错误信息
     */
    private String lastError;

    /**
     * 文档元数据
     */
    private Map<String, Object> metadata;
}

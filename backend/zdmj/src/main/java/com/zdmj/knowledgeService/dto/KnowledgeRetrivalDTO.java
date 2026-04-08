package com.zdmj.knowledgeService.dto;

import lombok.Data;
import java.util.Map;

@Data
public class KnowledgeRetrivalDTO {

    /**
     * 知识库向量ID
     */
    private Long id;
    
    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 文档块索引
     */
    private Integer chunkIndex;

    /**
     * 文档块内容
     */
    private String content;

    /** 相似度分数，
     * 范围约 [-1, 1]，
     */
    private Double score;

    /**
     * 文档块元数据
     */
    private Map<String, Object> metadata;
}

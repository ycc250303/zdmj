package com.zdmj.knowledgeService.dto;

import lombok.Data;

/**
 * 知识库响应（镜像当前 KnowledgeBases Entity JSON）
 */
@Data
public class KnowledgeBasesResponse {

    private Long id;

    private Long userId;

    private Integer scope;
}

package com.zdmj.knowledgeService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 检索 HTTP 响应的来源字段。
 * @param documentId 文档编号
 * @param source 受鉴权下载路径，系统资料为空
 * @param documentTitle 展示标题
 * @param docCategory 业务分类
 */
public record KnowledgeHitMetadata(@JsonProperty("DocumentId") String documentId, String source,
        String documentTitle, String docCategory) { }

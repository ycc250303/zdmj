package com.zdmj.knowledgeService.api;

/**
 * 报告 HTTP 响应的来源字段。
 * @param documentTitle 展示标题
 * @param docCategory 业务分类
 */
public record KnowledgeReferenceMetadata(String documentTitle, String docCategory) { }

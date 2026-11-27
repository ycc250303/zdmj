package com.zdmj.knowledgeService.dto;

/**
 * 已提交的系统资料导入摘要。
 * @param runId 清理目录编号
 * @param manifestHash 清单SHA-256
 * @param documents 同步后当前系统知识库来源的文档总数
 * @param chunks 同步后当前系统知识库来源的片段总数，包含保留的原片段
 */
public record SystemKnowledgeImportResult(String runId, String manifestHash, int documents, int chunks) {
}

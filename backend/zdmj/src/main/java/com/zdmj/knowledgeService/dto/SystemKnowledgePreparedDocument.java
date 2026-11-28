package com.zdmj.knowledgeService.dto;

import com.zdmj.knowledgeService.entity.KnowledgeVector;
import java.util.List;

/**
 * 已完成事务外准备的系统文档。
 * @param source 已验证的来源正文快照
 * @param rows 待发布片段；空列表表示正文未变且已有可用向量，发布时保留原片段
 */
public record SystemKnowledgePreparedDocument(SystemKnowledgeSource source, List<KnowledgeVector> rows) {
    public SystemKnowledgePreparedDocument { rows = List.copyOf(rows); }
}

package com.zdmj.knowledgeService.dto;

import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import java.util.List;
import java.util.Map;

/**
 * 系统导入比较与发布阶段的数据库快照，用于核对生成期间的并发修改。
 * @param documents 按来源标识关联的文档记录
 * @param chunkIds 按文档编号关联且已按编号排序的不可变片段编号列表
 */
public record SystemKnowledgeImportSnapshot(Map<String, KnowledgeDocument> documents, Map<Long, List<Long>> chunkIds) {
    public SystemKnowledgeImportSnapshot {
        documents = Map.copyOf(documents);
        chunkIds = Map.copyOf(chunkIds);
    }
}

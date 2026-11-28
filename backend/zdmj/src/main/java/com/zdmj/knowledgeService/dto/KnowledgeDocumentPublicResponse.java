package com.zdmj.knowledgeService.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识文档对外视图（单条 GET 与分页 {@code PageDTO#list} 元素结构一致）。
 *
 * @param id 文档ID
 * @param type 来源类型：1 上传文件、2 仓库链接、3 外部文档
 * @param content 原文件下载标识
 * @param fileStatus 原文件状态：0 写入中、1 可用、2 写入失败、3 删除中。
 * @param title 文档标题
 * @param embeddingStatus 向量化状态：PENDING、RUNNING、SUCCESS、FAILED；未知或未设置时为 null
 * @param lastEmbeddedAt 最后向量化时间
 * @param lastError 最后向量化错误信息
 * @param metadata 文档元数据
 */
public record KnowledgeDocumentPublicResponse(
        Long id,
        Integer type,
        String content,
        Integer fileStatus,
        String title,
        String embeddingStatus,
        LocalDateTime lastEmbeddedAt,
        String lastError,
        Map<String, Object> metadata) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public KnowledgeDocumentPublicResponse {
        metadata = metadata == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata));
    }
}

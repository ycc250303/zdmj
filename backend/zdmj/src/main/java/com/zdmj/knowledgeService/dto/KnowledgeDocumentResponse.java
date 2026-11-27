package com.zdmj.knowledgeService.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识文档创建/更新响应（镜像当前 KnowledgeDocument Entity JSON）
 *
 * @param id 文档编号，对应 knowledge_documents.id
 * @param userId 所属用户编号
 * @param type 来源类型，对应 type：1 上传文件、2 仓库链接、3 外部文档
 * @param content 原文件下载标识
 * @param fileStatus 原文件状态：0 写入中、1 可用、2 写入失败、3 删除中。
 * @param title 文档标题
 * @param contentHash 原文件 SHA-256，对应 content_hash
 * @param embeddingStatus 向量化状态，对应 embedding_status：1 等待、2 处理中、3 成功、4 失败
 * @param chunkCount 向量片段数量
 * @param lastEmbeddedAt 最后向量化时间
 * @param lastError 最后处理错误
 * @param metadata 文档元数据
 */
public record KnowledgeDocumentResponse(
        Long id,
        Long userId,
        Integer type,
        String content,
        Integer fileStatus,
        String title,
        String contentHash,
        Integer embeddingStatus,
        Integer chunkCount,
        LocalDateTime lastEmbeddedAt,
        String lastError,
        Map<String, Object> metadata) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public KnowledgeDocumentResponse {
        metadata = metadata == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata));
    }
}

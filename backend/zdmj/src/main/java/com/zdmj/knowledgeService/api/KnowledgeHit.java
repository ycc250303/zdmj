package com.zdmj.knowledgeService.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 检索命中的不可变公开结果，正文及来源信息取自同一次查询。
 *
 * @param id 向量编号，对应 knowledge_vectors.id，用于检索评测
 * @param documentId 文档编号
 * @param chunkIndex 分块编号
 * @param content 命中正文，供生成模型使用
 * @param score 余弦相似度
 * @param title 展示标题
 * @param docCategory 业务分类
 * @param downloadPath 本人可用原文件的受鉴权下载路径
 */
public record KnowledgeHit(Long id, Long documentId, Integer chunkIndex, String content, Double score,
        String title, String docCategory, String downloadPath) {

    /** 以命中快照创建报告引用，片段截断保留完整 Unicode 字符。 */
    @JsonIgnore
    public KnowledgeReference reference() {
        List<String> parts = new ArrayList<>();
        if (score != null) { parts.add(String.format(Locale.ROOT, "相似度 %.1f%%", score * 100)); }
        if (chunkIndex != null) { parts.add("块 #" + chunkIndex); }
        if (content != null && !content.isBlank()) {
            String text = content.trim().replaceAll("\\s+", " ");
            int end = text.offsetByCodePoints(0, Math.min(80, text.codePointCount(0, text.length())));
            parts.add(text.substring(0, end) + (end < text.length() ? "…" : ""));
        }
        return new KnowledgeReference(documentId, chunkIndex, score, title, String.join(" · ", parts),
                docCategory, downloadPath);
    }

    /** 兼容检索响应的必要来源字段，禁止携带原始 metadata。 */
    @JsonProperty("metadata")
    public KnowledgeHitMetadata metadata() {
        return new KnowledgeHitMetadata(String.valueOf(documentId), downloadPath, title, docCategory);
    }
}

package com.zdmj.knowledgeService.service;

import com.zdmj.common.context.CurrentActor;
public interface KnowledgeEmbeddingService {

    /**
     * 在事务外抽文本并向量化，再以短事务覆盖保存向量和文档状态。
     * 远程调用期间来源地址变化时，不写入这次算出的向量。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param knowledgeDocumentId 知识文档编号
     */
    void vectorizeAndStore(CurrentActor actor, Long knowledgeDocumentId);

    /**
     * 删除知识文档对应的向量。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param knowledgeDocumentId 知识文档 ID
     */
    void deleteVectors(CurrentActor actor, Long knowledgeDocumentId);

    /**
     * 将浮点数组转换为 PostgreSQL 向量字面量。
     *
     * @param vector 浮点数组
     * @return PostgreSQL 向量字符串；空数组返回 {@code null}
     */
    String toPgVector(float[] vector);
}

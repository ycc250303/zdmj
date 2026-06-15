package com.zdmj.knowledgeService.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.knowledgeService.entity.KnowledgeVector;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;

import java.util.List;

/**
 * 知识向量 Mapper
 * 继承 BaseMapper，基础 CRUD 由 MyBatis-Plus 提供。
 */
@Mapper
public interface KnowledgeVectorMapper extends BaseMapper<KnowledgeVector> {
    /**
     * 根据知识库ID删除向量
     * 
     * @param knowledgeId 知识库ID
     * @param userId      用户ID
     * @return 删除的行数
     */
    int deleteByDocumentIdAndUserId(@Param("DocumentId") Long DocumentId, @Param("userId") Long userId);

    /**
     * 批量插入向量
     * 
     * @param vectors 向量列表
     * @return 插入的行数
     */
    int batchInsert(@Param("rows") List<KnowledgeVector> rows);

    /**
     * 根据知识库ID删除向量
     * 
     * @param knowledgeId 知识库ID
     */
    void deleteByKnowledgeId(Long knowledgeId);

    /**
     * 按余弦距离做近似最近邻检索（依赖 knowledge_vectors 上的 HNSW 索引）。
     *
     * @param userId         数据隔离：只查当前用户向量
     * @param knowledgeId    当前用户知识库 id
     * @param queryEmbedding 查询向量，格式与入库一致："[0.1,0.2,...]"
     * @param limit          候选条数上限（可先取较大值，再在 Java 里按阈值过滤）
     */
    List<KnowledgeRetrivalDTO> searchBySimilarity(
            @Param("userId") Long userId,
            @Param("knowledgeId") Long knowledgeId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);

    /**
     * 在指定文档范围内做向量检索。
     */
    List<KnowledgeRetrivalDTO> searchBySimilarityInDocuments(
            @Param("userId") Long userId,
            @Param("knowledgeId") Long knowledgeId,
            @Param("documentIds") List<Long> documentIds,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);

    /**
     * 拉取指定文档在知识库下的全部向量块（按 chunk_index），用于「命中即带全篇」避免 Top-K 漏块。
     */
    List<KnowledgeRetrivalDTO> selectChunksByDocuments(
            @Param("userId") Long userId,
            @Param("knowledgeId") Long knowledgeId,
            @Param("documentIds") List<Long> documentIds);
}

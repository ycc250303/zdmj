package com.zdmj.knowledgeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;
import com.zdmj.knowledgeService.entity.KnowledgeVector;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 文档分块持久化；归属和检索范围统一通过所属文档检查。 */
@Mapper
public interface KnowledgeVectorMapper extends BaseMapper<KnowledgeVector> {
    /** 只删除指定用户私有文档的向量，返回实际删除数量。 */
    int deleteByDocumentIdAndUserId(@Param("documentId") Long documentId, @Param("userId") Long userId);

    /** 只删除SYSTEM、空用户且来源键为系统知识库的指定文档向量。 */
    int deleteSystemByDocumentId(@Param("documentId") Long documentId);

    /** 批量保存已经完成嵌入的分块，返回实际插入数量。 */
    int batchInsert(@Param("rows") List<KnowledgeVector> rows);

    /**
     * 根据文档范围执行余弦近邻检索，文档须可用且向量化成功。
     * @param userId 私有范围的用户编号；系统范围为空
     * @param scope 文档范围码值，由 KnowledgeScopeEnum 提供
     * @param queryEmbedding 查询向量的 pgvector 字面量
     * @param limit 候选数量上限
     * @return 已限定可见范围的候选片段
     */
    List<KnowledgeRetrivalDTO> searchBySimilarity(@Param("userId") Long userId, @Param("scope") Integer scope,
            @Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);

    /** 在私有文档编号范围内执行同样的授权检索。 */
    List<KnowledgeRetrivalDTO> searchBySimilarityInDocuments(@Param("userId") Long userId,
            @Param("scope") Integer scope, @Param("documentIds") List<Long> documentIds,
            @Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);
}

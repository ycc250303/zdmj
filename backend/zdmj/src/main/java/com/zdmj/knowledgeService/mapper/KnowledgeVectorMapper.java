package com.zdmj.knowledgeService.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.knowledgeService.entity.KnowledgeVector;

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
     * @param knowledgeId 知识库ID
     */
    void deleteByKnowledgeId(Long knowledgeId);
}

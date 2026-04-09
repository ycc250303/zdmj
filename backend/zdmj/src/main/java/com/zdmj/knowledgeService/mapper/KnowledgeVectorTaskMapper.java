package com.zdmj.knowledgeService.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;

/**
 * 向量任务 Mapper
 * 继承 BaseMapper，基础 CRUD 由 MyBatis-Plus 提供。
 */
@Mapper
public interface KnowledgeVectorTaskMapper extends BaseMapper<KnowledgeVectorTask> {
    /**
     * 根据知识库ID删除向量化任务
     * @param knowledgeId 知识库ID
     */
    void deleteByKnowledgeId(Long knowledgeId);
}


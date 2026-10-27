package com.zdmj.knowledgeService.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;

/**
 * 向量任务 Mapper。文档删除时清理历史任务行。
 */
@Mapper
public interface KnowledgeVectorTaskMapper extends BaseMapper<KnowledgeVectorTask> {

    /**
     * 按知识库删除向量化任务。
     *
     * @param knowledgeId 知识库 ID
     */
    void deleteByKnowledgeId(Long knowledgeId);
}

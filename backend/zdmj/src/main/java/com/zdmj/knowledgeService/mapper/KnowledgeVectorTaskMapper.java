package com.zdmj.knowledgeService.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 原子抢占任务：仅允许 PENDING -> RUNNING 一次。
     *
     * @param taskId 任务ID
     * @return 影响行数，1 表示抢占成功，0 表示已被其他执行者抢占或状态不匹配
     */
    int claimPendingTask(@Param("taskId") Long taskId);

    /**
     * 仅当任务处于 RUNNING 时标记成功。
     */
    int markTaskSuccess(@Param("taskId") Long taskId);

    /**
     * 仅当任务处于 RUNNING 时标记失败。
     */
    int markTaskFailed(@Param("taskId") Long taskId, @Param("errorMessage") String errorMessage);
}


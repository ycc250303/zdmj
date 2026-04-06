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
    // 预留：后续可按需要补充自定义查询方法
}


package com.zdmj.resumeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.resumeService.entity.ProjectExperience;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目经历Mapper接口
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有基础的 CRUD 方法
 */
@Mapper
public interface ProjectExperienceMapper extends BaseMapper<ProjectExperience> {

    /**
     * 根据用户ID查询所有项目经历
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param userId  用户ID
     * @param visible 是否可见（可选，如果提供则只返回 visible=true 的数据，null 则忽略此条件）
     * @return 项目经历列表
     */
    List<ProjectExperience> selectByUserId(@Param("userId") Long userId, @Param("visible") Boolean visible);

    /**
     * 根据用户ID查询项目经历ID列表
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param userId  用户ID
     * @param visible 是否可见（可选，如果提供则只返回 visible=true 的数据，null 则忽略此条件）
     * @return 项目经历ID列表
     */
    List<Long> selectProjectExperienceIds(@Param("userId") Long userId, @Param("visible") Boolean visible);

    /**
     * 根据简历ID查询项目经历列表
     * 从简历的 projects JSONB 数组中提取项目经历ID，然后关联查询项目经历详情
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param resumeId 简历ID
     * @return 项目经历列表
     */
    List<ProjectExperience> selectByResumeId(@Param("resumeId") Long resumeId);
}

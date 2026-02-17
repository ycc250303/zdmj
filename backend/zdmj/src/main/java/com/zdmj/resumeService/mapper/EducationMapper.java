package com.zdmj.resumeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.resumeService.entity.Education;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 教育经历Mapper接口
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有基础的 CRUD 方法：
 * - insert(entity) - 插入
 * - selectById(id) - 根据ID查询
 * - updateById(entity) - 根据ID更新
 * - deleteById(id) - 根据ID删除
 * - selectList(wrapper) - 条件查询列表
 * - 等等...
 */
@Mapper
public interface EducationMapper extends BaseMapper<Education> {

    /**
     * 根据用户ID查询所有教育经历
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param userId  用户ID
     * @param visible 是否可见（可选，如果提供则只返回 visible=true 的数据，null 则忽略此条件）
     * @return 教育经历列表
     */
    List<Education> selectByUserId(@Param("userId") Long userId, @Param("visible") Boolean visible);

    /**
     * 根据用户ID查询教育经历ID列表
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param userId  用户ID
     * @param visible 是否可见（可选，如果提供则只返回 visible=true 的数据，null 则忽略此条件）
     * @return 教育经历ID列表
     */
    List<Long> selectEducationIds(@Param("userId") Long userId, @Param("visible") Boolean visible);

    /**
     * 根据简历ID查询教育经历列表
     * 从简历的 educations JSONB 数组中提取教育经历ID，然后关联查询教育经历详情
     * 自定义方法，使用 MyBatis XML 实现
     *
     * @param resumeId 简历ID
     * @return 教育经历列表
     */
    List<Education> selectByResumeId(@Param("resumeId") Long resumeId);
}

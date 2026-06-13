package com.zdmj.resumeService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.resumeService.entity.ProjectExperience;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目经历 Mapper
 */
@Mapper
public interface ProjectExperienceMapper extends BaseMapper<ProjectExperience> {

    /**
     * 根据用户 ID 查询全部项目经历
     */
    List<ProjectExperience> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据用户 ID 和项目名称查询项目经历 ID
     */
    Long selectIdByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);
}

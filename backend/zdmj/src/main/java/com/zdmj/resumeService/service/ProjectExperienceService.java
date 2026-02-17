package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.ProjectExperienceDTO;
import com.zdmj.resumeService.entity.ProjectExperience;
import java.util.List;

/**
 * 项目经历服务接口
 */
public interface ProjectExperienceService {

    /**
     * 创建项目经历
     *
     * @param projectExperienceDTO 项目经历DTO
     * @return 创建的项目经历实体
     */
    ProjectExperience create(ProjectExperienceDTO projectExperienceDTO);

    /**
     * 根据ID查询项目经历
     *
     * @param id 项目经历ID
     * @return 项目经历实体
     */
    ProjectExperience getById(Long id);

    /**
     * 根据用户ID查询所有项目经历
     *
     * @return 项目经历列表
     */
    List<ProjectExperience> getByUserId();

    /**
     * 更新项目经历
     *
     * @param projectExperienceDTO 项目经历DTO
     * @return 更新后的项目经历实体
     */
    ProjectExperience update(ProjectExperienceDTO projectExperienceDTO);

    /**
     * 删除项目经历
     *
     * @param id 项目经历ID
     */
    void delete(Long id);
}

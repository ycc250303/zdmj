package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.ProjectExperienceRequest;
import com.zdmj.resumeService.dto.ProjectExperienceResponse;

import java.util.List;

/**
 * 项目经历服务接口
 */
public interface ProjectExperienceService {

    ProjectExperienceResponse create(ProjectExperienceRequest projectExperienceRequest);

    ProjectExperienceResponse getById(Long id);

    List<ProjectExperienceResponse> getByUserId();

    ProjectExperienceResponse update(ProjectExperienceRequest projectExperienceRequest);

    void delete(Long id);
}

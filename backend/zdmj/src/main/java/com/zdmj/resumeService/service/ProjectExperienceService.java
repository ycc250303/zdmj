package com.zdmj.resumeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.resumeService.dto.ProjectExperienceRequest;
import com.zdmj.resumeService.dto.ProjectExperienceResponse;
import com.zdmj.resumeService.entity.ProjectExperience;

/**
 * 项目经历服务接口
 */
public interface ProjectExperienceService {

    ProjectExperienceResponse create(CurrentActor actor, ProjectExperienceRequest projectExperienceRequest);

    ProjectExperienceResponse update(CurrentActor actor, ProjectExperienceRequest projectExperienceRequest);

    /**
     * 把已确认归属的项目经历写成本次请求，并校验结束日期。
     *
     * @param existing 当前用户已加载的项目经历
     * @param projectExperienceRequest 本次项目经历
     * @return 更新后的项目经历
     */
    ProjectExperienceResponse updateOwned(ProjectExperience existing, ProjectExperienceRequest projectExperienceRequest);

    void delete(CurrentActor actor, Long id);

    /**
     * 删除已确认属于当前用户的项目经历。
     *
     * @param id 项目经历编号
     */
    void deleteOwned(Long id);
}

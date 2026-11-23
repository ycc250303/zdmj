package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.resumeService.dto.ProjectExperienceRequest;
import com.zdmj.resumeService.dto.ProjectExperienceResponse;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.enums.ProjectStatusEnum;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceStructMapper;
import com.zdmj.resumeService.service.ProjectExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProjectExperienceServiceImpl extends ServiceImpl<ProjectExperienceMapper, ProjectExperience>
        implements ProjectExperienceService {

    private final ProjectExperienceStructMapper projectExperiencePatchMapper;

    @Override
    public ProjectExperienceResponse create(CurrentActor actor, ProjectExperienceRequest projectExperienceRequest) {
        Long userId = actor.userId();
        DateTimeUtil.rejectIfEndBeforeStart(
                projectExperienceRequest.startDate(),
                projectExperienceRequest.endDate(),
                ErrorCode.PROJECT_END_TIME_INVALID);

        ProjectExperience projectExperience = new ProjectExperience();
        projectExperience.setUserId(userId);
        projectExperience.setName(projectExperienceRequest.name());
        projectExperience.setStartDate(projectExperienceRequest.startDate());
        projectExperience.setEndDate(projectExperienceRequest.endDate());
        projectExperience.setRole(projectExperienceRequest.role());
        projectExperience.setDescription(projectExperienceRequest.description());
        projectExperience.setContribution(projectExperienceRequest.contribution());
        projectExperience.setTechStack(projectExperienceRequest.techStack());
        projectExperience.setHighlights(projectExperienceRequest.highlights());
        projectExperience.setUrl(projectExperienceRequest.url());
        projectExperience.setStatus(ProjectStatusEnum.COMMITTED.getCode());
        projectExperience.setLookupResult(null);
        boolean saved = save(projectExperience);
        if (!saved) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_ADD_FAILED);
        }
        log.info("用户 {} 添加项目经历成功: id={}", userId, projectExperience.getId());
        return convertToResponse(projectExperience);
    }

    @Override
    public ProjectExperienceResponse update(CurrentActor actor, ProjectExperienceRequest projectExperienceRequest) {
        Long userId = actor.userId();
        ProjectExperience projectExperience = requireProjectExperienceAndCheckOwnership(
                projectExperienceRequest.id(), userId, "修改");
        return updateOwned(projectExperience, projectExperienceRequest);
    }

    @Override
    public ProjectExperienceResponse updateOwned(ProjectExperience existing, ProjectExperienceRequest projectExperienceRequest) {
        projectExperiencePatchMapper.updateEntityFromDto(projectExperienceRequest, existing);
        DateTimeUtil.rejectIfEndBeforeStart(
                existing.getStartDate(),
                existing.getEndDate(),
                ErrorCode.PROJECT_END_TIME_INVALID);
        if (!updateById(existing)) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_UPDATE_FAILED);
        }
        log.info("用户 {} 更新项目经历成功: id={}", existing.getUserId(), existing.getId());
        return convertToResponse(existing);
    }

    @Override
    public void delete(CurrentActor actor, Long id) {
        Long userId = actor.userId();
        requireProjectExperienceAndCheckOwnership(id, userId, "删除");
        deleteOwned(id);
    }

    @Override
    public void deleteOwned(Long id) {
        if (!removeById(id)) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_DELETE_FAILED);
        }
        log.info("删除项目经历成功: id={}", id);
    }

    private ProjectExperienceResponse convertToResponse(ProjectExperience projectExperience) {
        return new ProjectExperienceResponse(projectExperience.getId(), projectExperience.getUserId(),
                projectExperience.getName(), projectExperience.getStartDate(), projectExperience.getEndDate(),
                projectExperience.getRole(), projectExperience.getDescription(),
                projectExperience.getContribution(), projectExperience.getTechStack(),
                projectExperience.getHighlights(), projectExperience.getUrl(), projectExperience.getStatus(),
                projectExperience.getLookupResult());
    }

    private ProjectExperience requireProjectExperience(Long id) {
        ProjectExperience projectExperience = baseMapper.selectById(id);
        if (projectExperience == null) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
        }
        return projectExperience;
    }

    private ProjectExperience requireProjectExperienceAndCheckOwnership(Long id, Long userId, String action) {
        ProjectExperience projectExperience = requireProjectExperience(id);
        if (!projectExperience.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION,
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人项目经历");
        }
        return projectExperience;
    }
}

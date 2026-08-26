package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.ProjectExperienceRequest;
import com.zdmj.resumeService.dto.ProjectExperienceResponse;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.enums.ProjectStatusEnum;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceStructMapper;
import com.zdmj.resumeService.service.ProjectExperienceService;
import com.zdmj.resumeService.support.ProjectHighlightsSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProjectExperienceServiceImpl extends ServiceImpl<ProjectExperienceMapper, ProjectExperience>
        implements ProjectExperienceService {

    private final ProjectExperienceStructMapper projectExperiencePatchMapper;

    @Override
    public ProjectExperienceResponse create(ProjectExperienceRequest projectExperienceRequest) {
        Long userId = UserHolder.requireUserId();
        ProjectExperience projectExperience = new ProjectExperience();
        projectExperience.setUserId(userId);
        projectExperience.setName(projectExperienceRequest.getName());
        projectExperience.setStartDate(projectExperienceRequest.getStartDate());
        projectExperience.setEndDate(projectExperienceRequest.getEndDate());
        projectExperience.setRole(projectExperienceRequest.getRole());
        projectExperience.setDescription(projectExperienceRequest.getDescription());
        projectExperience.setContribution(projectExperienceRequest.getContribution());
        projectExperience.setTechStack(projectExperienceRequest.getTechStack());
        projectExperience.setHighlights(
                ProjectHighlightsSupport.normalizeForStorage(projectExperienceRequest.getHighlights()));
        projectExperience.setUrl(projectExperienceRequest.getUrl());
        projectExperience.setStatus(ProjectStatusEnum.COMMITTED.getCode());
        projectExperience.setLookupResult(null);
        boolean saved = save(projectExperience);
        if (!saved) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_ADD_FAILED);
        }
        log.info("添加项目经历成功: {}", projectExperience.getName());
        return convertToResponse(projectExperience);
    }

    @Override
    public ProjectExperienceResponse getById(Long id) {
        return convertToResponse(requireProjectExperience(id));
    }

    @Override
    public List<ProjectExperienceResponse> getByUserId() {
        Long userId = UserHolder.requireUserId();
        return baseMapper.selectByUserId(userId).stream().map(this::convertToResponse).toList();
    }

    @Override
    public ProjectExperienceResponse update(ProjectExperienceRequest projectExperienceRequest) {
        Long userId = UserHolder.requireUserId();
        Long id = projectExperienceRequest.getId();
        ProjectExperience projectExperience = requireProjectExperienceAndCheckOwnership(id, userId, "修改");

        projectExperiencePatchMapper.updateEntityFromDto(projectExperienceRequest, projectExperience);
        projectExperience.setHighlights(
                ProjectHighlightsSupport.normalizeForStorage(projectExperience.getHighlights()));

        if (projectExperience.getStartDate() != null && projectExperience.getEndDate() != null) {
            if (projectExperience.getEndDate().isBefore(projectExperience.getStartDate())) {
                throw new BusinessException(ErrorCode.PROJECT_END_TIME_INVALID);
            }
        }

        boolean updated = updateById(projectExperience);
        if (!updated) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_UPDATE_FAILED);
        }

        log.info("更新项目经历成功: {}", projectExperience.getName());
        return convertToResponse(projectExperience);
    }

    @Override
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        ProjectExperience projectExperience = requireProjectExperienceAndCheckOwnership(id, userId, "删除");
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_DELETE_FAILED);
        }

        log.info("删除项目经历成功: {}", projectExperience.getName());
    }

    private ProjectExperienceResponse convertToResponse(ProjectExperience projectExperience) {
        ProjectExperienceResponse response = new ProjectExperienceResponse();
        BeanUtils.copyProperties(projectExperience, response);
        return response;
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
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(),
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人项目经历");
        }
        return projectExperience;
    }
}

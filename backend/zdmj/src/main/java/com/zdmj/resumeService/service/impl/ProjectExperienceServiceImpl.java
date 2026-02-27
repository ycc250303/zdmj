package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.BeanUtil;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.exception.ErrorCode;
import com.zdmj.exception.BusinessException;
import com.zdmj.resumeService.dto.ProjectExperienceDTO;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.service.ProjectExperienceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目经历服务实现类
 */
@Slf4j
@Service
public class ProjectExperienceServiceImpl extends ServiceImpl<ProjectExperienceMapper, ProjectExperience> 
        implements ProjectExperienceService {

    @Override
    public ProjectExperience create(ProjectExperienceDTO projectExperienceDTO) {
        Long userId = UserHolder.requireUserId();
        ProjectExperience projectExperience = new ProjectExperience();
        projectExperience.setUserId(userId);
        projectExperience.setName(projectExperienceDTO.getName());
        projectExperience.setStartDate(projectExperienceDTO.getStartDate());
        projectExperience.setEndDate(projectExperienceDTO.getEndDate());
        projectExperience.setRole(projectExperienceDTO.getRole());
        projectExperience.setDescription(projectExperienceDTO.getDescription());
        projectExperience.setContribution(projectExperienceDTO.getContribution());
        projectExperience.setTechStack(projectExperienceDTO.getTechStack());
        projectExperience.setHighlights(projectExperienceDTO.getHighlights());
        projectExperience.setUrl(projectExperienceDTO.getUrl());
        projectExperience.setVisible(projectExperienceDTO.getVisible());
        // 设置默认状态：1=committed已提交
        projectExperience.setStatus(1);
        projectExperience.setLookupResult(null);
        LocalDateTime now = DateTimeUtil.now();
        projectExperience.setCreatedAt(now);
        projectExperience.setUpdatedAt(now);
        boolean saved = save(projectExperience);
        if (!saved) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_ADD_FAILED);
        }
        log.info("添加项目经历成功: {}", projectExperience.getName());
        return projectExperience;
    }

    @Override
    public ProjectExperience getById(Long id) {
        return requireProjectExperience(id);
    }

    @Override
    public List<ProjectExperience> getByUserId() {
        Long userId = UserHolder.requireUserId();
        return baseMapper.selectByUserId(userId, null);
    }

    @Override
    public ProjectExperience update(ProjectExperienceDTO projectExperienceDTO) {
        Long userId = UserHolder.requireUserId();
        Long id = projectExperienceDTO.getId();
        if (id == null) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_ID_EMPTY);
        }
        ProjectExperience projectExperience = requireProjectExperienceAndCheckOwnership(id, userId, "修改");

        Long savedId = projectExperience.getId();
        BeanUtil.copyNonNullProperties(projectExperienceDTO, projectExperience);
        projectExperience.setId(savedId);

        if (projectExperience.getStartDate() != null && projectExperience.getEndDate() != null) {
            if (projectExperience.getEndDate().isBefore(projectExperience.getStartDate())) {
                throw new BusinessException(ErrorCode.PROJECT_END_TIME_INVALID);
            }
        }

        LocalDateTime now = DateTimeUtil.now();
        projectExperience.setUpdatedAt(now);

        boolean updated = updateById(projectExperience);
        if (!updated) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_UPDATE_FAILED);
        }

        log.info("更新项目经历成功: {}", projectExperience.getName());
        return projectExperience;
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

    /**
     * 校验项目经历是否存在，返回项目经历实体
     *
     * @param id 项目经历ID
     * @return 项目经历实体
     * @throws BusinessException 如果项目经历不存在
     */
    private ProjectExperience requireProjectExperience(Long id) {
        ProjectExperience projectExperience = baseMapper.selectById(id);
        if (projectExperience == null) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
        }
        return projectExperience;
    }

    /**
     * 校验项目经历是否存在且用户有权限操作，返回项目经历实体
     *
     * @param id     项目经历ID
     * @param userId 用户ID
     * @param action 操作类型（用于错误提示）
     * @return 项目经历实体
     * @throws BusinessException 如果项目经历不存在或用户无权限
     */
    private ProjectExperience requireProjectExperienceAndCheckOwnership(Long id, Long userId, String action) {
        ProjectExperience projectExperience = requireProjectExperience(id);
        if (!projectExperience.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(), ErrorCode.NO_PERMISSION.getMessage() + action + "他人项目经历");
        }
        return projectExperience;
    }
}

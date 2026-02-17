package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.BeanUtil;
import com.zdmj.common.util.DateTimeUtil;
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
public class ProjectExperienceServiceImpl implements ProjectExperienceService {

    private final ProjectExperienceMapper projectExperienceMapper;

    public ProjectExperienceServiceImpl(ProjectExperienceMapper projectExperienceMapper) {
        this.projectExperienceMapper = projectExperienceMapper;
    }

    @Override
    public ProjectExperience create(ProjectExperienceDTO projectExperienceDTO) {
        Long userId = requireUserId();
        ProjectExperience projectExperience = new ProjectExperience();
        projectExperience.setUserId(userId);
        projectExperience.setName(projectExperienceDTO.getName());
        projectExperience.setStartDate(projectExperienceDTO.getStartDate());
        projectExperience.setEndDate(projectExperienceDTO.getEndDate());
        projectExperience.setRole(projectExperienceDTO.getRole());
        projectExperience.setDescription(projectExperienceDTO.getDescription());
        projectExperience.setTechStack(projectExperienceDTO.getTechStack());
        projectExperience.setHighlights(projectExperienceDTO.getHighlights());
        projectExperience.setUrl(projectExperienceDTO.getUrl());
        projectExperience.setVisible(projectExperienceDTO.getVisible());
        LocalDateTime now = DateTimeUtil.now();
        projectExperience.setCreatedAt(now);
        projectExperience.setUpdatedAt(now);
        int result = projectExperienceMapper.insert(projectExperience);
        if (result <= 0) {
            throw new BusinessException(500, "添加项目经历失败");
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
        Long userId = requireUserId();
        return projectExperienceMapper.selectByUserId(userId, null);
    }

    @Override
    public ProjectExperience update(ProjectExperienceDTO projectExperienceDTO) {
        Long userId = requireUserId();
        Long id = projectExperienceDTO.getId();
        if (id == null) {
            throw new BusinessException(400, "项目经历ID不能为空");
        }
        ProjectExperience projectExperience = requireProjectExperienceAndCheckOwnership(id, userId, "修改");

        Long savedId = projectExperience.getId();
        BeanUtil.copyNonNullProperties(projectExperienceDTO, projectExperience);
        projectExperience.setId(savedId);

        if (projectExperience.getStartDate() != null && projectExperience.getEndDate() != null) {
            if (projectExperience.getEndDate().isBefore(projectExperience.getStartDate())) {
                throw new BusinessException(400, "项目结束时间不能早于开始时间");
            }
        }

        LocalDateTime now = DateTimeUtil.now();
        projectExperience.setUpdatedAt(now);

        int result = projectExperienceMapper.updateById(projectExperience);
        if (result <= 0) {
            throw new BusinessException(500, "更新项目经历失败");
        }
        log.info("更新项目经历成功: {}", projectExperience.getName());
        return projectExperience;
    }

    @Override
    public void delete(Long id) {
        Long userId = requireUserId();
        ProjectExperience projectExperience = requireProjectExperienceAndCheckOwnership(id, userId, "删除");
        int result = projectExperienceMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(500, "删除项目经历失败");
        }
        log.info("删除项目经历成功: {}", projectExperience.getName());
    }

    /**
     * 校验用户是否已登录，返回用户ID
     *
     * @return 用户ID
     * @throws BusinessException 如果用户未登录
     */
    private Long requireUserId() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "用户未登录");
        }
        return userId;
    }

    /**
     * 校验项目经历是否存在，返回项目经历实体
     *
     * @param id 项目经历ID
     * @return 项目经历实体
     * @throws BusinessException 如果项目经历不存在
     */
    private ProjectExperience requireProjectExperience(Long id) {
        ProjectExperience projectExperience = projectExperienceMapper.selectById(id);
        if (projectExperience == null) {
            throw new BusinessException(404, "项目经历不存在");
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
            throw new BusinessException(403, "无权" + action + "他人项目经历");
        }
        return projectExperience;
    }
}

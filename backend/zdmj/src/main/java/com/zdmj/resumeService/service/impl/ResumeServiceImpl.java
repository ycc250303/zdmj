package com.zdmj.resumeService.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.util.DtoConverter;
import com.zdmj.exception.BusinessException;
import com.zdmj.resumeService.dto.*;
import com.zdmj.resumeService.entity.*;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.mapper.EducationMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.mapper.ResumeMapper;
import com.zdmj.resumeService.mapper.SkillMapper;
import com.zdmj.resumeService.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 简历服务实现类
 */
@Slf4j
@Service
public class ResumeServiceImpl implements ResumeService {
    private final ResumeMapper resumeMapper;
    private final EducationMapper educationMapper;
    private final ProjectExperienceMapper projectExperienceMapper;
    private final CareerMapper careerMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillMapper skillMapper;

    public ResumeServiceImpl(ResumeMapper resumeMapper, EducationMapper educationMapper,
            ProjectExperienceMapper projectExperienceMapper, CareerMapper careerMapper, SkillMapper skillMapper) {
        this.educationMapper = educationMapper;
        this.projectExperienceMapper = projectExperienceMapper;
        this.careerMapper = careerMapper;
        this.resumeMapper = resumeMapper;
        this.skillMapper = skillMapper;
    }

    @Override
    public Resume create(ResumeDTO resumeDTO) {
        Long userId = requireUserId();
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setName(resumeDTO.getName());
        resume.setSkillId(resumeDTO.getSkillId());

        List<Long> educationIds = educationMapper.selectEducationIds(userId, true);
        List<Long> careerIds = careerMapper.selectCareerIds(userId, true);
        List<Long> projectExperienceIds = projectExperienceMapper.selectProjectExperienceIds(userId, true);

        // 将 ID 列表转换为 JSON 数组字符串
        resume.setEducations(convertIdsToJson(educationIds));
        resume.setCareers(convertIdsToJson(careerIds));
        resume.setProjects(convertIdsToJson(projectExperienceIds));

        LocalDateTime now = DateTimeUtil.now();
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        int result = resumeMapper.insert(resume);
        if (result <= 0) {
            throw new BusinessException(500, "创建简历失败");
        }
        log.info("创建简历成功: {}", resume.getName());
        return resume;
    }

    @Override
    public Resume getById(Long id) {
        return requireResume(id);
    }

    @Override
    public List<Resume> getByUserId() {
        Long userId = requireUserId();
        return resumeMapper.selectByUserId(userId);
    }

    @Override
    public Resume update(ResumeDTO resumeDTO) {
        Long userId = requireUserId();
        Long id = resumeDTO.getId();
        if (id == null) {
            throw new BusinessException(400, "简历ID不能为空");
        }

        Resume resume = requireResumeAndCheckOwnership(id, userId, "修改");
        resume.setName(resumeDTO.getName());
        resume.setSkillId(resumeDTO.getSkillId());
        resume.setUpdatedAt(DateTimeUtil.now());
        int result = resumeMapper.updateById(resume);
        if (result <= 0) {
            throw new BusinessException(500, "更新简历失败");
        }
        log.info("更新简历成功: {}", resume.getName());
        return resume;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = requireUserId();
        Resume resume = requireResumeAndCheckOwnership(id, userId, "删除");
        int result = resumeMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(500, "删除简历失败");
        }
        log.info("删除简历成功: {}", resume.getName());
    }

    @Override
    public ResumeFullContentDTO getResumeFullContentById(Long id) {
        Long userId = requireUserId();
        Resume resume = requireResumeAndCheckOwnership(id, userId, "查询");

        // 创建简历完整内容DTO
        ResumeFullContentDTO resumeFullContentDTO = new ResumeFullContentDTO();
        resumeFullContentDTO.setId(resume.getId());
        resumeFullContentDTO.setName(resume.getName());

        // 获取技能、教育、工作、项目等信息
        Skill skill = resume.getSkillId() != null ? skillMapper.selectById(resume.getSkillId()) : null;
        List<Education> educations = educationMapper.selectByResumeId(id);
        List<Career> careers = careerMapper.selectByResumeId(id);
        List<ProjectExperience> projects = projectExperienceMapper.selectByResumeId(id);

        resumeFullContentDTO.setSkill(skill != null ? convertSkillToDTO(skill) : null);
        resumeFullContentDTO.setEducations(educations.stream()
                .map(education -> DtoConverter.toDTO(education, EducationDTO.class))
                .collect(Collectors.toList()));
        resumeFullContentDTO.setCareers(careers.stream()
                .map(career -> DtoConverter.toDTO(career, CareerDTO.class))
                .collect(Collectors.toList()));
        resumeFullContentDTO.setProjects(projects.stream()
                .map(project -> DtoConverter.toDTO(project, ProjectExperienceDTO.class))
                .collect(Collectors.toList()));

        return resumeFullContentDTO;
    }

    @Override
    public List<ResumeFullContentDTO> getResumeFullContent() {
        Long userId = requireUserId();
        List<Resume> resumes = resumeMapper.selectByUserId(userId);
        return resumes.stream()
                .map(resume -> getResumeFullContentById(resume.getId()))
                .collect(Collectors.toList());
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
     * 校验简历是否存在，返回简历实体
     *
     * @param id 简历ID
     * @return 简历实体
     * @throws BusinessException 如果简历不存在
     */
    private Resume requireResume(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        return resume;
    }

    /**
     * 校验简历是否存在且用户有权限操作，返回简历实体
     *
     * @param id     简历ID
     * @param userId 用户ID
     * @param action 操作类型（用于错误提示）
     * @return 简历实体
     * @throws BusinessException 如果简历不存在或用户无权限
     */
    private Resume requireResumeAndCheckOwnership(Long id, Long userId, String action) {
        Resume resume = requireResume(id);
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权" + action + "他人简历");
        }
        return resume;
    }

    /**
     * 将 ID 列表转换为 JSON 数组字符串
     * 例如：[1, 2, 3] -> "[1,2,3]"
     *
     * @param ids ID 列表
     * @return JSON 数组字符串，如果列表为空则返回 "[]"
     */
    private String convertIdsToJson(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return "[]";
            }
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw new BusinessException(500, "ID 列表转换失败: " + e.getMessage());
        }
    }

    /**
     * 将 JSON 数组字符串转换为 ID 列表
     * 例如："[1,2,3]" -> [1, 2, 3]
     *
     * @param json JSON 数组字符串
     * @return ID 列表，如果字符串为空或 null 则返回空列表
     */
    private List<Long> convertJsonToIds(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            throw new BusinessException(400, "JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 将 Skill 实体转换为 SkillDTO
     * 手写转换函数，处理特殊字段（content: JSON String -> List<SkillItemDTO>）
     */
    private SkillDTO convertSkillToDTO(Skill skill) {
        if (skill == null) {
            return null;
        }

        SkillDTO dto = new SkillDTO();
        dto.setId(skill.getId());
        dto.setName(skill.getName());

        // 将 JSON 字符串转换为 List<SkillItemDTO>
        if (skill.getContent() != null && !skill.getContent().isEmpty()) {
            try {
                List<SkillItemDTO> contentList = objectMapper.readValue(
                        skill.getContent(),
                        new TypeReference<List<SkillItemDTO>>() {
                        });
                dto.setContent(contentList);
            } catch (Exception e) {
                log.warn("技能内容解析失败: {}", e.getMessage());
                dto.setContent(java.util.Collections.emptyList());
            }
        } else {
            dto.setContent(java.util.Collections.emptyList());
        }

        return dto;
    }

}

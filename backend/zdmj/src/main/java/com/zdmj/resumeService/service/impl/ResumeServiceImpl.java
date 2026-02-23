package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.CosUtil;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.util.DtoConverter;
import com.zdmj.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 简历服务实现类
 */
@Slf4j
@Service
public class ResumeServiceImpl extends ServiceImpl<ResumeMapper, Resume> implements ResumeService {
    private final EducationMapper educationMapper;
    private final ProjectExperienceMapper projectExperienceMapper;
    private final CareerMapper careerMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillMapper skillMapper;

    public ResumeServiceImpl(EducationMapper educationMapper,
            ProjectExperienceMapper projectExperienceMapper, CareerMapper careerMapper, SkillMapper skillMapper) {
        this.educationMapper = educationMapper;
        this.projectExperienceMapper = projectExperienceMapper;
        this.careerMapper = careerMapper;
        this.skillMapper = skillMapper;
    }

    @Override
    public Resume create(ResumeDTO resumeDTO) {
        Long userId = UserHolder.requireUserId();

        // 检查是否存在同名简历
        if (baseMapper.existsByName(userId, resumeDTO.getName(), null)) {
            throw new BusinessException(400, "简历名称已存在，请使用其他名称");
        }

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setName(resumeDTO.getName());
        resume.setSkillId(resumeDTO.getSkillId());

        List<Long> educationIds = educationMapper.selectEducationIds(userId, true);
        List<Long> careerIds = careerMapper.selectCareerIds(userId, true);
        List<Long> projectExperienceIds = projectExperienceMapper.selectProjectExperienceIds(userId, true);

        // 直接设置List，TypeHandler会自动处理JSONB转换
        resume.setEducations(educationIds);
        resume.setCareers(careerIds);
        resume.setProjects(projectExperienceIds);

        LocalDateTime now = DateTimeUtil.now();
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        boolean saved = save(resume);
        if (!saved) {
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
        Long userId = UserHolder.requireUserId();
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public Resume update(ResumeDTO resumeDTO) {
        Long userId = UserHolder.requireUserId();
        Long id = resumeDTO.getId();
        if (id == null) {
            throw new BusinessException(400, "简历ID不能为空");
        }

        Resume resume = requireResumeAndCheckOwnership(id, userId, "修改");

        // 如果简历名称发生变化，检查是否存在同名简历（排除当前简历）
        if (!resume.getName().equals(resumeDTO.getName())) {
            if (baseMapper.existsByName(userId, resumeDTO.getName(), id)) {
                throw new BusinessException(400, "简历名称已存在，请使用其他名称");
            }
        }

        // 重新查询用户当前所有可见的教育经历、工作经历、项目经历ID
        // 这样当用户修改了某个经历的 visible 字段后，更新简历时会自动同步这些变化
        List<Long> educationIds = educationMapper.selectEducationIds(userId, true);
        List<Long> careerIds = careerMapper.selectCareerIds(userId, true);
        List<Long> projectExperienceIds = projectExperienceMapper.selectProjectExperienceIds(userId, true);

        resume.setName(resumeDTO.getName());
        resume.setSkillId(resumeDTO.getSkillId());
        // 直接设置List，TypeHandler会自动处理JSONB转换
        resume.setEducations(educationIds);
        resume.setCareers(careerIds);
        resume.setProjects(projectExperienceIds);
        resume.setUpdatedAt(DateTimeUtil.now());

        boolean updated = updateById(resume);
        if (!updated) {
            throw new BusinessException(500, "更新简历失败");
        }
        log.info("更新简历成功: {}", resume.getName());

        // 注意：不再清除缓存，因为简历基础信息和列表已不使用缓存

        return resume;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        Resume resume = requireResumeAndCheckOwnership(id, userId, "删除");
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(500, "删除简历失败");
        }
        log.info("删除简历成功: {}", resume.getName());

        // 注意：不再清除缓存，因为简历基础信息和列表已不使用缓存
    }

    @Override
    public ResumeContentDTO getResumeContentById(Long id) {
        Long userId = UserHolder.requireUserId();

        // 直接查询数据库，不使用缓存
        // 原因：用户频繁修改项目经历、工作经历、教育经历等，写操作频繁
        // 如果使用缓存，会导致缓存频繁失效，命中率低，反而增加系统负担
        // 查询简历完整内容的SQL并不复杂（只是几个LEFT JOIN），数据库查询性能可以接受
        Resume resume = requireResumeAndCheckOwnership(id, userId, "查询");

        // 创建简历完整内容DTO
        ResumeContentDTO resumeContentDTO = new ResumeContentDTO();
        resumeContentDTO.setId(resume.getId());
        resumeContentDTO.setName(resume.getName());

        // 获取技能、教育、工作、项目等信息
        Skill skill = resume.getSkillId() != null ? skillMapper.selectById(resume.getSkillId()) : null;
        List<Education> educations = educationMapper.selectByResumeId(id);
        List<Career> careers = careerMapper.selectByResumeId(id);
        List<ProjectExperience> projects = projectExperienceMapper.selectByResumeId(id);

        resumeContentDTO.setSkill(skill != null ? convertSkillToDTO(skill) : null);
        resumeContentDTO.setEducations(educations.stream()
                .map(education -> DtoConverter.toDTO(education, EducationDTO.class))
                .collect(Collectors.toList()));
        resumeContentDTO.setCareers(careers.stream()
                .map(career -> DtoConverter.toDTO(career, CareerDTO.class))
                .collect(Collectors.toList()));
        resumeContentDTO.setProjects(projects.stream()
                .map(project -> DtoConverter.toDTO(project, ProjectExperienceDTO.class))
                .collect(Collectors.toList()));

        return resumeContentDTO;
    }

    /**
     * 查询所有简历完整内容
     *
     * @return 简历完整内容列表
     */
    @Override
    public List<ResumeContentDTO> getResumeContentList() {
        Long userId = UserHolder.requireUserId();

        // 直接查询数据库，不使用缓存
        // 原因：用户频繁修改项目经历、工作经历、教育经历等，写操作频繁
        // 如果使用缓存，会导致缓存频繁失效，命中率低，反而增加系统负担
        List<Resume> resumes = baseMapper.selectByUserId(userId);
        List<ResumeContentDTO> result = resumes.stream()
                .map(resume -> getResumeContentById(resume.getId()))
                .collect(Collectors.toList());

        return result;
    }

    /**
     * 校验简历是否存在，返回简历实体
     *
     * @param id 简历ID
     * @return 简历实体
     * @throws BusinessException 如果简历不存在
     */
    private Resume requireResume(Long id) {
        Resume resume = baseMapper.selectById(id);
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

    @Override
    public Map<String, String> uploadResumeFile(MultipartFile file) {
        // 验证文件
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }

        Long userId = UserHolder.requireUserId();

        // 生成文件路径（对象键），使用 "resume-用户ID" 作为前缀
        String key = CosUtil.generateKey("resume-" + userId, file.getOriginalFilename());

        // 上传文件到COS
        String uploadedKey = CosUtil.uploadFile(file, key);

        // 获取文件访问URL
        String fileUrl = CosUtil.getFileUrl(uploadedKey);

        // 构建返回结果
        Map<String, String> result = new HashMap<>();
        result.put("key", uploadedKey);
        result.put("url", fileUrl);
        result.put("fileName", file.getOriginalFilename());
        result.put("fileSize", String.valueOf(file.getSize()));
        result.put("contentType", file.getContentType());

        log.info("简历文件上传成功，key: {}, url: {}", uploadedKey, fileUrl);

        return result;
    }

}

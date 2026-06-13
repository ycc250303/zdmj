package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.LlmInputLimits;
import com.zdmj.common.ai.ModelEnum;
import com.zdmj.common.ai.prompt.PromptNames;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.resumeService.dto.*;
import com.zdmj.resumeService.enums.AwardTypeEnum;
import com.zdmj.resumeService.entity.*;
import com.zdmj.resumeService.mapper.AwardMapper;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.mapper.EducationMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.mapper.ResumeMapper;
import com.zdmj.resumeService.mapper.SkillMapper;
import com.zdmj.resumeService.service.AwardService;
import com.zdmj.userAuthService.entity.User;
import com.zdmj.userAuthService.mapper.UserMapper;
import com.zdmj.resumeService.service.CareerService;
import com.zdmj.resumeService.service.EducationService;
import com.zdmj.resumeService.service.ProjectExperienceService;
import com.zdmj.resumeService.service.ResumeService;
import com.zdmj.resumeService.service.SkillService;
import com.zdmj.resumeService.support.AwardImportSupport;
import com.zdmj.resumeService.support.ProjectHighlightsSupport;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 简历服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeServiceImpl extends ServiceImpl<ResumeMapper, Resume> implements ResumeService {

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s{3,}");
    private static final Pattern PRESENT_END = Pattern.compile("^(至今|现在|present|current|now)$",
            Pattern.CASE_INSENSITIVE);

    private final EducationMapper educationMapper;
    private final ProjectExperienceMapper projectExperienceMapper;
    private final CareerMapper careerMapper;
    private final AwardMapper awardMapper;
    private final SkillMapper skillMapper;
    private final UserMapper userMapper;
    private final ChatUtil chatUtil;
    private final ObjectMapper objectMapper;
    private final EducationService educationService;
    private final CareerService careerService;
    private final AwardService awardService;
    private final ProjectExperienceService projectExperienceService;
    private final SkillService skillService;
    private final Validator validator;

    @Override
    public Resume create(ResumeDTO resumeDTO) {
        Long userId = UserHolder.requireUserId();

        if (baseMapper.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.RESUME_ALREADY_EXISTS);
        }

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setSkillId(resumeDTO.getSkillId());

        boolean saved = save(resume);
        if (!saved) {
            throw new BusinessException(ErrorCode.RESUME_CREATE_FAILED);
        }
        log.info("创建简历成功: userId={}", userId);
        return resume;
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

        Resume resume = requireResumeAndCheckOwnership(id, userId, "修改");

        resume.setSkillId(resumeDTO.getSkillId());
        boolean updated = updateById(resume);
        if (!updated) {
            throw new BusinessException(ErrorCode.RESUME_UPDATE_FAILED);
        }
        log.info("更新简历成功: resumeId={}", resume.getId());

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
            throw new BusinessException(ErrorCode.RESUME_DELETE_FAILED);
        }
        log.info("删除简历成功: resumeId={}", resume.getId());

        // 注意：不再清除缓存，因为简历基础信息和列表已不使用缓存
    }

    @Override
    public List<ResumeContentDTO> getResumeContentList() {
        Long userId = UserHolder.requireUserId();
        Resume resume = baseMapper.selectOneByUserId(userId);
        if (resume == null) {
            return List.of();
        }
        return List.of(buildResumeContent(resume));
    }

    @Override
    public ResumeContentDTO getMyResumeContent() {
        Long userId = UserHolder.requireUserId();
        Resume resume = ensureResumeForUser(userId);
        return buildResumeContent(resume);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeContentDTO saveMyResumeContent(ResumeContentSaveRequest request) {
        Long userId = UserHolder.requireUserId();
        Resume resume = ensureResumeForUser(userId);

        Long skillId = syncSkill(userId, resume.getSkillId(), request.getSkill());
        resume.setSkillId(skillId);

        syncEducations(userId, request.getEducations());
        syncCareers(userId, request.getCareers());
        syncProjects(userId, request.getProjects());
        syncAwards(userId, request.getAwards());
        syncPersonalInfo(userId, request.getPersonalInfo());

        List<Award> savedAwards = awardMapper.selectByUserId(userId);
        resume.setAwards(savedAwards.stream().map(Award::getId).collect(Collectors.toList()));

        boolean updated = updateById(resume);
        if (!updated) {
            throw new BusinessException(ErrorCode.RESUME_UPDATE_FAILED);
        }
        log.info("全量保存简历成功: userId={}, resumeId={}", userId, resume.getId());
        return buildResumeContent(resume);
    }

    private ResumeContentDTO buildResumeContent(Resume resume) {
        ResumeContentDTO resumeContentDTO = new ResumeContentDTO();
        resumeContentDTO.setId(resume.getId());

        Skill skill = resume.getSkillId() != null ? skillMapper.selectById(resume.getSkillId()) : null;
        List<Education> educations = educationMapper.selectByUserId(resume.getUserId());
        List<Career> careers = careerMapper.selectByUserId(resume.getUserId());
        List<ProjectExperience> projects = projectExperienceMapper.selectByUserId(resume.getUserId());
        List<Award> awards = awardMapper.selectByUserId(resume.getUserId());

        resumeContentDTO.setSkill(skill != null ? convertSkillToDTO(skill) : null);
        resumeContentDTO.setEducations(educations.stream()
                .map(education -> convertSimpleEntityToDTO(education, EducationDTO.class))
                .collect(Collectors.toList()));
        resumeContentDTO.setCareers(careers.stream()
                .map(career -> convertSimpleEntityToDTO(career, CareerDTO.class))
                .collect(Collectors.toList()));
        resumeContentDTO.setProjects(projects.stream()
                .map(project -> convertSimpleEntityToDTO(project, ProjectExperienceDTO.class))
                .collect(Collectors.toList()));
        resumeContentDTO.setAwards(awards.stream()
                .map(award -> convertSimpleEntityToDTO(award, AwardDTO.class))
                .collect(Collectors.toList()));
        resumeContentDTO.setPersonalInfo(buildPersonalInfo(resume.getUserId()));
        return resumeContentDTO;
    }

    private ResumePersonalInfoDTO buildPersonalInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return new ResumePersonalInfoDTO();
        }
        ResumePersonalInfoDTO info = new ResumePersonalInfoDTO();
        info.setName(user.getName());
        info.setPhone(user.getPhone());
        info.setHomepageUrl(user.getWebsite());
        info.setPreferredWorkCity(user.getPreferredWorkCity());
        return info;
    }

    private void syncPersonalInfo(Long userId, ResumePersonalInfoDTO info) {
        if (info == null) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (info.getName() != null) {
            user.setName(info.getName().trim());
        }
        if (info.getPhone() != null) {
            user.setPhone(info.getPhone().trim());
        }
        if (info.getHomepageUrl() != null) {
            user.setWebsite(info.getHomepageUrl().trim());
        }
        if (info.getPreferredWorkCity() != null) {
            user.setPreferredWorkCity(info.getPreferredWorkCity().trim());
        }
        if (userMapper.updateById(user) <= 0) {
            throw new BusinessException(ErrorCode.USER_REGISTER_FAILED);
        }
    }

    private Resume ensureResumeForUser(Long userId) {
        Resume resume = baseMapper.selectOneByUserId(userId);
        if (resume != null) {
            return resume;
        }

        Long skillId = ensureDefaultSkillId(userId);
        Resume newResume = new Resume();
        newResume.setUserId(userId);
        newResume.setSkillId(skillId);
        boolean saved = save(newResume);
        if (!saved) {
            throw new BusinessException(ErrorCode.RESUME_CREATE_FAILED);
        }
        log.info("自动创建默认简历: userId={}", userId);
        return newResume;
    }

    private Long ensureDefaultSkillId(Long userId) {
        List<Skill> skills = skillMapper.selectByUserId(userId);
        if (!skills.isEmpty()) {
            return skills.get(0).getId();
        }
        SkillDTO skillDTO = new SkillDTO();
        SkillItemDTO item = new SkillItemDTO();
        item.setType("专业技能");
        item.setContent(List.of("待补充"));
        skillDTO.setContent(List.of(item));
        return skillService.create(skillDTO).getId();
    }

    private Long syncSkill(Long userId, Long currentSkillId, SkillDTO skillDto) {
        if (skillDto.getId() != null) {
            skillService.update(skillDto);
            return skillDto.getId();
        }
        if (currentSkillId != null) {
            skillDto.setId(currentSkillId);
            skillService.update(skillDto);
            return currentSkillId;
        }
        return skillService.create(skillDto).getId();
    }

    private void syncEducations(Long userId, List<EducationDTO> incoming) {
        List<Education> existing = educationMapper.selectByUserId(userId);
        Set<Long> incomingIds = collectIncomingIds(incoming);
        for (Education item : existing) {
            if (!incomingIds.contains(item.getId())) {
                educationService.delete(item.getId());
            }
        }
        for (EducationDTO dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                educationService.create(dto);
            } else {
                requireOwnedEducation(dto.getId(), userId);
                educationService.update(dto);
            }
        }
    }

    private void syncCareers(Long userId, List<CareerDTO> incoming) {
        List<Career> existing = careerMapper.selectByUserId(userId);
        Set<Long> incomingIds = collectIncomingIds(incoming);
        for (Career item : existing) {
            if (!incomingIds.contains(item.getId())) {
                careerService.delete(item.getId());
            }
        }
        for (CareerDTO dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                careerService.create(dto);
            } else {
                requireOwnedCareer(dto.getId(), userId);
                careerService.update(dto);
            }
        }
    }

    private void syncProjects(Long userId, List<ProjectExperienceDTO> incoming) {
        List<ProjectExperience> existing = projectExperienceMapper.selectByUserId(userId);
        Set<Long> incomingIds = collectIncomingIds(incoming);
        for (ProjectExperience item : existing) {
            if (!incomingIds.contains(item.getId())) {
                projectExperienceService.delete(item.getId());
            }
        }
        for (ProjectExperienceDTO dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                projectExperienceService.create(dto);
            } else {
                requireOwnedProject(dto.getId(), userId);
                projectExperienceService.update(dto);
            }
        }
    }

    private void syncAwards(Long userId, List<AwardDTO> incoming) {
        List<Award> existing = awardMapper.selectByUserId(userId);
        Set<Long> incomingIds = collectIncomingIds(incoming);
        for (Award item : existing) {
            if (!incomingIds.contains(item.getId())) {
                awardService.delete(item.getId());
            }
        }
        for (AwardDTO dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                awardService.create(dto);
            } else {
                requireOwnedAward(dto.getId(), userId);
                awardService.update(dto);
            }
        }
    }

    private <T> Set<Long> collectIncomingIds(List<T> incoming) {
        Set<Long> ids = new HashSet<>();
        for (T item : incoming) {
            Long id = extractId(item);
            if (id != null) {
                if (!ids.add(id)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "提交的数据中存在重复的经历 ID");
                }
            }
        }
        return ids;
    }

    private Long extractId(Object dto) {
        if (dto instanceof EducationDTO educationDTO) {
            return educationDTO.getId();
        }
        if (dto instanceof CareerDTO careerDTO) {
            return careerDTO.getId();
        }
        if (dto instanceof ProjectExperienceDTO projectDTO) {
            return projectDTO.getId();
        }
        if (dto instanceof AwardDTO awardDTO) {
            return awardDTO.getId();
        }
        return null;
    }

    private <T> void validateForSave(T dto, Class<?>... groups) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto, groups);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), detail);
        }
    }

    private void requireOwnedEducation(Long id, Long userId) {
        Education education = educationMapper.selectById(id);
        if (education == null) {
            throw new BusinessException(ErrorCode.EDUCATION_NOT_FOUND);
        }
        if (!education.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private void requireOwnedCareer(Long id, Long userId) {
        Career career = careerMapper.selectById(id);
        if (career == null) {
            throw new BusinessException(ErrorCode.CAREER_NOT_FOUND);
        }
        if (!career.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private void requireOwnedProject(Long id, Long userId) {
        ProjectExperience project = projectExperienceMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private void requireOwnedAward(Long id, Long userId) {
        Award award = awardMapper.selectById(id);
        if (award == null) {
            throw new BusinessException(ErrorCode.AWARD_NOT_FOUND);
        }
        if (!award.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    @Override
    public ResumeImportParseResultDTO parseImport(ResumeImportParseRequest request) {
        log.info("开始识别简历结构化字段");
        UserHolder.requireUserId();
        List<String> warnings = new ArrayList<>();
        String sourceText = resolveImportSourceText(request);
        sourceText = preprocessImportText(sourceText, warnings);

        ResumeImportParseResultDTO parsed;
        try {
            parsed = chatUtil.chatStructuredOnceWithPlatformModel(
                    sourceText,
                    PromptNames.RESUME_IMPORT_PARSE,
                    null,
                    ResumeImportParseResultDTO.class,
                    ModelEnum.DEEPSEEK_FLASH);
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalStateException e) {
            log.error("简历结构化输出解析失败", e);
            throw new BusinessException(ErrorCode.RESUME_IMPORT_PARSE_FAILED);
        } catch (Exception e) {
            log.error("简历识别失败", e);
            throw new BusinessException(ErrorCode.RESUME_IMPORT_PARSE_FAILED);
        }

        normalizeImportResult(parsed, sourceText, warnings);
        return parsed;
    }

    private String resolveImportSourceText(ResumeImportParseRequest request) {
        if (StringUtils.hasText(request.getPdfUrl())) {
            log.info("简历识别：从 PDF 解析文本");
            try {
                String text = PdfParserUtil.extractTextFromUrl(request.getPdfUrl().trim());
                if (!StringUtils.hasText(text)) {
                    throw new BusinessException(ErrorCode.RESUME_IMPORT_TEXT_EMPTY);
                }
                return text;
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("PDF 解析失败", e);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "PDF 解析失败，请检查文件是否合法");
            }
        }
        if (request.getRawText() != null) {
            String text = request.getRawText().trim();
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(ErrorCode.RESUME_IMPORT_TEXT_EMPTY);
            }
            log.info("简历识别：使用纯文本");
            return text;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "必须提供 pdfUrl 或 rawText");
    }

    private String preprocessImportText(String sourceText, List<String> warnings) {
        String normalized = WHITESPACE_RUN.matcher(sourceText.trim()).replaceAll("\n\n");
        int limit = LlmInputLimits.RESUME_IMPORT_TEXT_TRUNCATE_CHARS;
        if (normalized.length() > limit) {
            warnings.add("简历文本过长，已截断至前 " + limit + " 个字符，部分经历可能未识别");
            normalized = normalized.substring(0, limit);
        }
        return normalized;
    }

    private void normalizeImportResult(ResumeImportParseResultDTO result, String sourceText, List<String> warnings) {
        if (result == null) {
            throw new BusinessException(ErrorCode.RESUME_IMPORT_PARSE_FAILED);
        }
        if (result.getWarnings() == null) {
            result.setWarnings(new ArrayList<>());
        }
        result.getWarnings().addAll(0, warnings);

        if (result.getEducations() == null) {
            result.setEducations(new ArrayList<>());
        }
        if (result.getCareers() == null) {
            result.setCareers(new ArrayList<>());
        }
        if (result.getProjects() == null) {
            result.setProjects(new ArrayList<>());
        }
        if (result.getAwards() == null) {
            result.setAwards(new ArrayList<>());
        }

        List<ResumeImportParseResultDTO.EducationItem> educations = new ArrayList<>();
        for (ResumeImportParseResultDTO.EducationItem item : result.getEducations()) {
            if (item == null || !StringUtils.hasText(item.getSchool())) {
                continue;
            }
            item.setDegree(normalizeDegree(item.getDegree()));
            item.setStartDate(formatDateString(parseFlexibleDate(item.getStartDate())));
            item.setEndDate(formatDateString(parseFlexibleDate(item.getEndDate())));
            educations.add(item);
        }
        result.setEducations(educations);

        List<ResumeImportParseResultDTO.CareerItem> careers = new ArrayList<>();
        for (ResumeImportParseResultDTO.CareerItem item : result.getCareers()) {
            if (item == null || !StringUtils.hasText(item.getCompany())) {
                continue;
            }
            item.setStartDate(formatDateString(parseFlexibleDate(item.getStartDate())));
            item.setEndDate(formatDateString(parseFlexibleDate(item.getEndDate())));
            careers.add(item);
        }
        result.setCareers(careers);

        List<ResumeImportParseResultDTO.ProjectItem> projects = new ArrayList<>();
        for (ResumeImportParseResultDTO.ProjectItem item : result.getProjects()) {
            if (item == null || !StringUtils.hasText(item.getName())) {
                continue;
            }
            item.setStartDate(formatDateString(parseFlexibleDate(item.getStartDate())));
            item.setEndDate(formatDateString(parseFlexibleDate(item.getEndDate())));
            item.setHighlights(normalizeHighlights(item.getHighlights()));
            projects.add(item);
        }
        result.setProjects(projects);

        List<ResumeImportParseResultDTO.AwardItem> awards = new ArrayList<>();
        for (ResumeImportParseResultDTO.AwardItem item : result.getAwards()) {
            if (item == null) {
                continue;
            }
            if (!StringUtils.hasText(item.getName()) && StringUtils.hasText(item.getDescription())) {
                item.setName(item.getDescription().trim());
                item.setDescription(null);
            }
            if (!StringUtils.hasText(item.getName())) {
                continue;
            }
            item.setAwardType(AwardImportSupport.resolveAwardType(item.getAwardType(), item.getName()));
            LocalDate awardDate = parseAwardDate(item, warnings);
            if (awardDate == null) {
                continue;
            }
            item.setAwardDate(formatDateString(awardDate));
            awards.add(item);
        }
        supplementAwardsFromProjects(projects, awards, warnings);
        AwardImportSupport.supplementScholarshipsFromSourceText(
                sourceText,
                awards,
                warnings,
                item -> parseAwardDate(item, warnings),
                (context, name) -> extractYearAsJanFirst(context, warnings, name),
                this::formatDateString);
        result.setAwards(awards);

        if (result.getSkill() != null && result.getSkill().getContent() == null) {
            result.getSkill().setContent(new ArrayList<>());
        }
    }

    private Integer normalizeDegree(Integer degree) {
        if (degree == null || degree < 1 || degree > 6) {
            return 6;
        }
        return degree;
    }

    private LocalDate parseAwardDate(ResumeImportParseResultDTO.AwardItem item, List<String> warnings) {
        LocalDate date = parseFlexibleDate(item.getAwardDate());
        if (date != null) {
            return date;
        }
        date = parseFlexibleDate(item.getName());
        if (date != null) {
            return date;
        }
        date = parseFlexibleDate(item.getDescription());
        if (date != null) {
            return date;
        }
        date = extractYearAsJanFirst(item.getName(), warnings, item.getName());
        if (date != null) {
            return date;
        }
        return extractYearAsJanFirst(item.getDescription(), warnings, item.getName());
    }

    private LocalDate extractYearAsJanFirst(String text, List<String> warnings, String awardName) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        var range = Pattern.compile("(\\d{4})\\s*[-~至到—]\\s*(\\d{4})").matcher(text);
        if (range.find()) {
            int year = Integer.parseInt(range.group(1));
            if (StringUtils.hasText(awardName)) {
                warnings.add("奖项「" + awardName + "」日期为区间，已取起始年 " + year + "-01-01");
            }
            return LocalDate.of(year, 1, 1);
        }
        LocalDate chineseDate = parseChineseDate(text);
        if (chineseDate != null) {
            return chineseDate;
        }
        if (isChineseYearOnly(text)) {
            var yearOnly = Pattern.compile("^(\\d{4})年$").matcher(text.trim());
            if (yearOnly.matches()) {
                int year = Integer.parseInt(yearOnly.group(1));
                if (StringUtils.hasText(awardName)) {
                    warnings.add("奖项「" + awardName + "」仅有年份，已设为 " + year + "-01-01");
                }
                return LocalDate.of(year, 1, 1);
            }
        }
        var yearInText = Pattern.compile("(\\d{4})\\s*年").matcher(text);
        if (yearInText.find()) {
            int year = Integer.parseInt(yearInText.group(1));
            if (StringUtils.hasText(awardName)) {
                warnings.add("奖项「" + awardName + "」仅有年份，已设为 " + year + "-01-01");
            }
            return LocalDate.of(year, 1, 1);
        }
        var bareYear = Pattern.compile("(?:^|\\D)(19\\d{2}|20\\d{2})(?:\\D|$)").matcher(text);
        if (bareYear.find()) {
            int year = Integer.parseInt(bareYear.group(1));
            if (StringUtils.hasText(awardName)) {
                warnings.add("奖项「" + awardName + "」仅有年份，已设为 " + year + "-01-01");
            }
            return LocalDate.of(year, 1, 1);
        }
        return null;
    }

    private void supplementAwardsFromProjects(List<ResumeImportParseResultDTO.ProjectItem> projects,
            List<ResumeImportParseResultDTO.AwardItem> awards, List<String> warnings) {
        Set<String> existingNames = awards.stream()
                .map(ResumeImportParseResultDTO.AwardItem::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Pattern awardPattern = Pattern.compile("(?:获|获得|荣获)\\s*([^，。；;\\n]+?奖)");
        for (ResumeImportParseResultDTO.ProjectItem project : projects) {
            String text = highlightsAsPlainText(project.getHighlights());
            if (!StringUtils.hasText(text) || !text.contains("奖")) {
                continue;
            }
            var matcher = awardPattern.matcher(text);
            while (matcher.find()) {
                String awardName = matcher.group(1).trim();
                if (!StringUtils.hasText(awardName) || existingNames.contains(awardName)) {
                    continue;
                }
                ResumeImportParseResultDTO.AwardItem item = new ResumeImportParseResultDTO.AwardItem();
                item.setName(awardName);
                item.setAwardType(AwardImportSupport.inferAwardTypeFromName(awardName));
                LocalDate awardDate = parseAwardDate(item, warnings);
                if (awardDate == null) {
                    awardDate = extractYearAsJanFirst(text, warnings, awardName);
                }
                if (awardDate == null) {
                    continue;
                }
                item.setAwardDate(formatDateString(awardDate));
                awards.add(item);
                existingNames.add(awardName);
                warnings.add("已从项目亮点补充奖项：" + awardName);
            }
        }
    }

    private String highlightsAsPlainText(Object highlights) {
        if (highlights == null) {
            return null;
        }
        if (highlights instanceof String s) {
            return ProjectHighlightsSupport.toPlainText(s);
        }
        if (highlights instanceof List<?> list) {
            return ProjectHighlightsSupport.toPlainText(ProjectHighlightsSupport.normalizeForStorage(list));
        }
        return ProjectHighlightsSupport.toPlainText(String.valueOf(highlights));
    }

    private LocalDate parseFlexibleDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (PRESENT_END.matcher(value).matches()) {
            return null;
        }

        LocalDate chineseDate = parseChineseDate(value);
        if (chineseDate != null || isChineseYearOnly(value)) {
            return chineseDate;
        }

        value = value.replace('.', '-').replace('/', '-');
        try {
            if (value.matches("\\d{4}")) {
                return null;
            }
            if (value.matches("\\d{4}-\\d{1,2}")) {
                return YearMonth.parse(value, DateTimeFormatter.ofPattern("yyyy-M")).atDay(1);
            }
            if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                String[] parts = value.split("-");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                if (day <= 0) {
                    day = 1;
                }
                return LocalDate.of(year, month, day);
            }
            LocalDate parsed = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return parsed;
        } catch (DateTimeParseException | NumberFormatException e) {
            log.warn("无法解析日期（需至少到月份粒度）: {}", raw);
            return null;
        }
    }

    /**
     * 解析中文日期：有年月无日取 1 号；仅有年份返回 null。
     */
    private LocalDate parseChineseDate(String value) {
        var yearMonthDay = Pattern.compile("^(\\d{4})年(\\d{1,2})月(\\d{1,2})日?$").matcher(value);
        if (yearMonthDay.matches()) {
            return LocalDate.of(
                    Integer.parseInt(yearMonthDay.group(1)),
                    Integer.parseInt(yearMonthDay.group(2)),
                    Integer.parseInt(yearMonthDay.group(3)));
        }
        var yearMonth = Pattern.compile("^(\\d{4})年(\\d{1,2})月$").matcher(value);
        if (yearMonth.matches()) {
            return LocalDate.of(
                    Integer.parseInt(yearMonth.group(1)),
                    Integer.parseInt(yearMonth.group(2)),
                    1);
        }
        return null;
    }

    private boolean isChineseYearOnly(String value) {
        return Pattern.compile("^\\d{4}年$").matcher(value).matches();
    }

    private String formatDateString(LocalDate date) {
        return date == null ? null : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * highlights 归一化为 JSON 数组字符串（与 project_experiences.highlights 存储一致）。
     */
    private Object normalizeHighlights(Object highlights) {
        if (highlights == null) {
            return null;
        }
        if (highlights instanceof List<?> list) {
            return ProjectHighlightsSupport.normalizeForStorage(list);
        }
        if (highlights instanceof String s) {
            return ProjectHighlightsSupport.normalizeForStorage(s);
        }
        return ProjectHighlightsSupport.normalizeForStorage(String.valueOf(highlights));
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
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
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
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(),
                    ErrorCode.NO_PERMISSION.getMessage() + action + "他人简历");
        }
        return resume;
    }

    /**
     * 将 Skill 实体转换为 SkillDTO
     * content 已改为强类型对象数组，可直接复制
     */
    private SkillDTO convertSkillToDTO(Skill skill) {
        if (skill == null) {
            return null;
        }

        SkillDTO dto = new SkillDTO();
        dto.setId(skill.getId());
        dto.setContent(skill.getContent() != null ? skill.getContent() : java.util.Collections.emptyList());
        return dto;
    }

    /**
     * 轻量通用转换：用于字段名一致且无需复杂转换的实体->DTO映射
     */
    private <S, T> T convertSimpleEntityToDTO(S source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("对象转换失败: " + targetClass.getSimpleName(), e);
        }
    }

}

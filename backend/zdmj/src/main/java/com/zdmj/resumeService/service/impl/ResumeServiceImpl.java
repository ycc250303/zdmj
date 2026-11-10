package com.zdmj.resumeService.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.resumeService.dto.*;
import com.zdmj.resumeService.entity.*;
import com.zdmj.resumeService.mapper.AwardMapper;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.mapper.EducationMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.mapper.ResumeContentRow;
import com.zdmj.resumeService.mapper.ResumeMapper;
import com.zdmj.resumeService.mapper.SkillMapper;
import com.zdmj.resumeService.service.AwardService;
import com.zdmj.userAuthService.dto.UserUpdateRequest;
import com.zdmj.userAuthService.service.UserLoginService;
import com.zdmj.resumeService.service.CareerService;
import com.zdmj.resumeService.service.EducationService;
import com.zdmj.resumeService.service.ProjectExperienceService;
import com.zdmj.resumeService.service.ResumeService;
import com.zdmj.resumeService.service.SkillService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class ResumeServiceImpl implements ResumeService {

    private static final String IMPORT_PROMPT = "resume-import-parse";

    private static final Pattern PRESENT_END = Pattern.compile("^(至今|现在|present|current|now)$",
            Pattern.CASE_INSENSITIVE);

    private final EducationMapper educationMapper;
    private final ProjectExperienceMapper projectExperienceMapper;
    private final CareerMapper careerMapper;
    private final AwardMapper awardMapper;
    private final SkillMapper skillMapper;
    private final ResumeMapper resumeMapper;
    private final UserLoginService userService;
    private final ModelGateway modelGateway;
    private final EducationService educationService;
    private final CareerService careerService;
    private final AwardService awardService;
    private final ProjectExperienceService projectExperienceService;
    private final SkillService skillService;
    private final Validator validator;
    private final PdfParserUtil pdfParserUtil;
    private final ObjectMapper objectMapper;

    @Override
    public List<ResumeContentResponse> getResumeContentList() {
        Long userId = UserHolder.requireUserId();
        ResumeContentRow row = resumeMapper.selectContentByUserId(userId);
        if (row == null || !hasStoredContent(row)) {
            return List.of();
        }
        return List.of(toContentResponse(row));
    }

    @Override
    public ResumeContentResponse getMyResumeContent() {
        Long userId = UserHolder.requireUserId();
        return toContentResponse(requireUserContent(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeContentResponse saveMyResumeContent(ResumeContentSaveRequest request) {
        Long userId = UserHolder.requireUserId();
        requireUserContent(userId);
        syncSkills(userId, request.getSkills());
        syncEducations(userId, request.getEducations());
        syncCareers(userId, request.getCareers());
        syncProjects(userId, request.getProjects());
        syncAwards(userId, request.getAwards());
        syncPersonalInfo(userId, request.getPersonalInfo());
        log.info("全量保存简历成功: userId={}", userId);
        return toContentResponse(requireUserContent(userId));
    }

    /**
     * 读取当前用户的整份简历行。用户不存在时立即终止。
     *
     * @param userId 用户编号
     * @return 查询行
     */
    private ResumeContentRow requireUserContent(Long userId) {
        ResumeContentRow row = resumeMapper.selectContentByUserId(userId);
        if (row == null) {
            throw new IllegalStateException("当前用户不存在，无法读取简历");
        }
        return row;
    }

    private boolean hasStoredContent(ResumeContentRow row) {
        return hasItems(row.getSkills())
                || hasItems(row.getEducations())
                || hasItems(row.getCareers())
                || hasItems(row.getProjects())
                || hasItems(row.getAwards());
    }

    private boolean hasItems(String json) {
        return StringUtils.hasText(json) && !"[]".equals(json.trim());
    }

    /**
     * 把一次查询结果组装成整份简历响应。
     *
     * @param row 查询行，不能为空
     * @return 整份简历
     */
    private ResumeContentResponse toContentResponse(ResumeContentRow row) {
        ResumeContentResponse response = new ResumeContentResponse();
        response.setSkills(readSection(row.getSkills(), SkillResponse.class));
        response.setEducations(readSection(row.getEducations(), EducationRequest.class));
        response.setCareers(readSection(row.getCareers(), CareerRequest.class));
        response.setProjects(readSection(row.getProjects(), ProjectExperienceRequest.class));
        response.setAwards(readSection(row.getAwards(), AwardRequest.class));
        ResumePersonalInfoDTO info = new ResumePersonalInfoDTO();
        info.setName(row.getPersonalName());
        info.setPhone(row.getPersonalPhone());
        info.setHomepageUrl(row.getPersonalWebsite());
        info.setPreferredWorkCity(row.getPreferredWorkCity());
        response.setPersonalInfo(info);
        return response;
    }

    private <T> List<T> readSection(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("简历内容解析失败", e);
        }
    }

    private void syncPersonalInfo(Long userId, ResumePersonalInfoDTO info) {
        if (info == null) {
            return;
        }
        UserUpdateRequest update = new UserUpdateRequest();
        update.setName(info.getName());
        update.setPhone(info.getPhone());
        update.setWebsite(info.getHomepageUrl());
        update.setPreferredWorkCity(info.getPreferredWorkCity());
        userService.updateProfile(userId, update);
    }

    /**
     * 按提交列表替换当前用户的技能。列表不能为空，至少包含一项有效名称。
     *
     * @param userId 用户编号
     * @param incoming 技能请求，不能为 null
     */
    private void syncSkills(Long userId, List<SkillRequest> incoming) {
        if (incoming == null || incoming.stream().noneMatch(item -> item != null && StringUtils.hasText(item.getName()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请至少填写一项技能");
        }
        List<Skill> existing = skillMapper.selectByUserId(userId);
        Set<Long> incomingIds = collectIncomingIds(incoming);
        for (Skill item : existing) {
            if (!incomingIds.contains(item.getId())) {
                skillService.delete(item.getId());
            }
        }
        for (SkillRequest dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                skillService.create(dto);
            } else {
                requireOwnedSkill(dto.getId(), userId);
                skillService.update(dto);
            }
        }
    }

    /**
     * 同步教育经历
     * 
     * @param userId 用户编号
     * @param incoming 教育经历请求
     */
    private void syncEducations(Long userId, List<EducationRequest> incoming) {
        List<Education> existing = educationMapper.selectByUserId(userId);
        Set<Long> incomingIds = collectIncomingIds(incoming);
        for (Education item : existing) {
            if (!incomingIds.contains(item.getId())) {
                educationService.delete(item.getId());
            }
        }
        for (EducationRequest dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                educationService.create(dto);
            } else {
                requireOwnedEducation(dto.getId(), userId);
                educationService.update(dto);
            }
        }
    }

    private void syncCareers(Long userId, List<CareerRequest> incoming) {
        List<Career> existing = careerMapper.selectByUserId(userId);
        Set<Long> incomingIds = collectIncomingIds(incoming);
        for (Career item : existing) {
            if (!incomingIds.contains(item.getId())) {
                careerService.delete(item.getId());
            }
        }
        for (CareerRequest dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                careerService.create(dto);
            } else {
                requireOwnedCareer(dto.getId(), userId);
                careerService.update(dto);
            }
        }
    }

    private void syncProjects(Long userId, List<ProjectExperienceRequest> incoming) {
        List<ProjectExperience> existing = projectExperienceMapper.selectByUserId(userId);
        Set<Long> incomingIds = collectIncomingIds(incoming);
        for (ProjectExperience item : existing) {
            if (!incomingIds.contains(item.getId())) {
                projectExperienceService.delete(item.getId());
            }
        }
        for (ProjectExperienceRequest dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                projectExperienceService.create(dto);
            } else {
                requireOwnedProject(dto.getId(), userId);
                projectExperienceService.update(dto);
            }
        }
    }

    private void syncAwards(Long userId, List<AwardRequest> incoming) {
        List<Award> existing = awardMapper.selectByUserId(userId);
        Set<Long> incomingIds = collectIncomingIds(incoming);
        for (Award item : existing) {
            if (!incomingIds.contains(item.getId())) {
                awardService.delete(item.getId());
            }
        }
        for (AwardRequest dto : incoming) {
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
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "提交的数据中存在重复的经历 ID");
                }
            }
        }
        return ids;
    }

    private Long extractId(Object dto) {
        if (dto instanceof EducationRequest educationDTO) {
            return educationDTO.getId();
        }
        if (dto instanceof CareerRequest careerDTO) {
            return careerDTO.getId();
        }
        if (dto instanceof ProjectExperienceRequest projectDTO) {
            return projectDTO.getId();
        }
        if (dto instanceof AwardRequest awardDTO) {
            return awardDTO.getId();
        }
        if (dto instanceof SkillRequest skillDTO) {
            return skillDTO.getId();
        }
        return null;
    }

    private <T> void validateForSave(T dto, Class<?>... groups) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto, groups);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, detail);
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

    private void requireOwnedSkill(Long id, Long userId) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) {
            throw new BusinessException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (!skill.getUserId().equals(userId)) {
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

    /**
     * 解析导入的简历
     * 
     * @param request 导入请求
     * @return 解析后的简历
     */
    @Override
    public ResumeImportParseResponse parseImport(ResumeImportParseRequest request) {
        log.info("开始识别简历结构化字段");
        Long userId = UserHolder.requireUserId();
        String sourceText = PdfParserUtil.normalizeExtractedText(resolveImportSourceText(request));

        ResumeImportParseResponse parsed;
        try {
            parsed = modelGateway.generateStructured(
                    CurrentActor.of(userId),
                    new ModelRequest(sourceText, IMPORT_PROMPT, null), ResumeImportParseResponse.class);
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalStateException e) {
            log.error("简历结构化输出解析失败", e);
            throw new BusinessException(ErrorCode.RESUME_IMPORT_PARSE_FAILED);
        } catch (Exception e) {
            log.error("简历识别失败", e);
            throw new BusinessException(ErrorCode.RESUME_IMPORT_PARSE_FAILED);
        }

        normalizeImportResult(parsed);
        return parsed;
    }

    /**
     * 解析导入的简历文本
     * 
     * @param request 导入请求
     * @return 解析后的文本
     */
    private String resolveImportSourceText(ResumeImportParseRequest request) {
        if (StringUtils.hasText(request.getPdfUrl())) {
            log.info("简历识别：从 PDF 解析文本");
            try {
                String text = pdfParserUtil.extractTextFromUrl(request.getPdfUrl().trim());
                if (!StringUtils.hasText(text)) {
                    throw new BusinessException(ErrorCode.RESUME_IMPORT_TEXT_EMPTY);
                }
                return text;
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("PDF 解析失败", e);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF 解析失败，请检查文件是否合法");
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
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必须提供 pdfUrl 或 rawText");
    }

    /**
     * 整理模型返回的简历。能解析到月份的日期写成 YYYY-MM-DD；奖项缺少月份时补成该年 1 月 1 日。
     *
     * @param result 导入结果
     */
    private void normalizeImportResult(ResumeImportParseResponse result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.RESUME_IMPORT_PARSE_FAILED);
        }

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

        List<ResumeImportParseResponse.EducationItem> educations = new ArrayList<>();
        for (ResumeImportParseResponse.EducationItem item : result.getEducations()) {
            if (item == null || !StringUtils.hasText(item.getSchool())) {
                continue;
            }
            item.setDegree(normalizeDegree(item.getDegree()));
            item.setStartDate(formatDateString(parseFlexibleDate(item.getStartDate())));
            item.setEndDate(formatDateString(parseFlexibleDate(item.getEndDate())));
            educations.add(item);
        }
        result.setEducations(educations);

        List<ResumeImportParseResponse.CareerItem> careers = new ArrayList<>();
        for (ResumeImportParseResponse.CareerItem item : result.getCareers()) {
            if (item == null || !StringUtils.hasText(item.getCompany())) {
                continue;
            }
            item.setStartDate(formatDateString(parseFlexibleDate(item.getStartDate())));
            item.setEndDate(formatDateString(parseFlexibleDate(item.getEndDate())));
            careers.add(item);
        }
        result.setCareers(careers);

        List<ResumeImportParseResponse.ProjectItem> projects = new ArrayList<>();
        for (ResumeImportParseResponse.ProjectItem item : result.getProjects()) {
            if (item == null || !StringUtils.hasText(item.getName())) {
                continue;
            }
            item.setStartDate(formatDateString(parseFlexibleDate(item.getStartDate())));
            item.setEndDate(formatDateString(parseFlexibleDate(item.getEndDate())));
            projects.add(item);
        }
        result.setProjects(projects);

        List<ResumeImportParseResponse.AwardItem> awards = new ArrayList<>();
        for (ResumeImportParseResponse.AwardItem item : result.getAwards()) {
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
            item.setName(item.getName().trim());
            item.setAwardDate(formatDateString(parseAwardDate(item)));
            awards.add(item);
        }
        result.setAwards(awards);

        if (result.getSkills() == null) {
            result.setSkills(new ArrayList<>());
        }
        List<ResumeImportParseResponse.SkillImportItem> skills = new ArrayList<>();
        for (ResumeImportParseResponse.SkillImportItem item : result.getSkills()) {
            if (item == null || !StringUtils.hasText(item.getName())) {
                continue;
            }
            item.setName(item.getName().trim());
            item.setDescription(StringUtils.hasText(item.getDescription()) ? item.getDescription().trim() : null);
            skills.add(item);
        }
        result.setSkills(skills);
    }

    private Integer normalizeDegree(Integer degree) {
        if (degree == null || degree < 1 || degree > 6) {
            return 6;
        }
        return degree;
    }

    /**
     * 解析奖项日期。字段本身能解析到月份时直接采用；否则从名称、说明中取完整日期，仍没有月份时取出现的年份并补成 1 月 1 日。
     *
     * @param item 奖项
     * @return 解析后的日期，无法识别时返回 null
     */
    private LocalDate parseAwardDate(ResumeImportParseResponse.AwardItem item) {
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
        date = extractYearAsJanFirst(item.getName());
        if (date != null) {
            return date;
        }
        return extractYearAsJanFirst(item.getDescription());
    }

    /**
     * 从文本中提取年份并补成该年 1 月 1 日。年份区间取起始年。
     *
     * @param text 奖项名称或说明
     * @return 补全后的日期，没有年份时返回 null
     */
    private LocalDate extractYearAsJanFirst(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        var range = Pattern.compile("(\\d{4})\\s*[-~至到—]\\s*(\\d{4})").matcher(text);
        if (range.find()) {
            return LocalDate.of(Integer.parseInt(range.group(1)), 1, 1);
        }
        LocalDate chineseDate = parseChineseDate(text);
        if (chineseDate != null) {
            return chineseDate;
        }
        if (isChineseYearOnly(text)) {
            var yearOnly = Pattern.compile("^(\\d{4})年$").matcher(text.trim());
            if (yearOnly.matches()) {
                return LocalDate.of(Integer.parseInt(yearOnly.group(1)), 1, 1);
            }
        }
        var yearInText = Pattern.compile("(\\d{4})\\s*年").matcher(text);
        if (yearInText.find()) {
            return LocalDate.of(Integer.parseInt(yearInText.group(1)), 1, 1);
        }
        var bareYear = Pattern.compile("(?:^|\\D)(19\\d{2}|20\\d{2})(?:\\D|$)").matcher(text);
        if (bareYear.find()) {
            return LocalDate.of(Integer.parseInt(bareYear.group(1)), 1, 1);
        }
        return null;
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

}

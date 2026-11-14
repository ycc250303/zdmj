package com.zdmj.resumeService.service.impl;

import java.io.InputStream;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.context.CurrentActor;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
    public List<ResumeContentResponse> getResumeContentList(CurrentActor actor) {
        Long userId = actor.userId();
        ResumeContentRow row = resumeMapper.selectContentByUserId(userId);
        if (!hasStoredContent(row)) {
            return List.of();
        }
        return List.of(toContentResponse(row));
    }

    @Override
    public ResumeContentResponse getMyResumeContent(CurrentActor actor) {
        Long userId = actor.userId();
        ResumeContentRow row = resumeMapper.selectContentByUserId(userId);
        return toContentResponse(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeContentResponse saveMyResumeContent(CurrentActor actor, ResumeContentSaveRequest request) {
        Long userId = actor.userId();
        syncSkills(actor, request.getSkills());
        syncEducations(actor, request.getEducations());
        syncCareers(actor, request.getCareers());
        syncProjects(actor, request.getProjects());
        syncAwards(actor, request.getAwards());
        syncPersonalInfo(actor, request.getPersonalInfo());
        log.info("全量保存简历成功: userId={}", userId);
        return toContentResponse(resumeMapper.selectContentByUserId(userId));
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
        if (row == null || !hasStoredContent(row)) {
            return null;
        }
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

    /**
     * 将 JSON 字符串转换为列表。
     *
     * @param json JSON 字符串
     * @param type 类型
     * @return 列表
     */
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

    private void syncPersonalInfo(CurrentActor actor, ResumePersonalInfoDTO info) {
        Long userId = actor.userId();
        if (info == null) {
            return;
        }
        UserUpdateRequest update = new UserUpdateRequest();
        update.setName(info.getName());
        update.setPhone(info.getPhone());
        update.setWebsite(info.getHomepageUrl());
        update.setPreferredWorkCity(info.getPreferredWorkCity());
        userService.updateProfile(actor, userId, update);
    }

    /**
     * 按提交列表替换当前用户的技能。空列表会删除已有技能。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param incoming 技能请求，不能为 null
     */
    private void syncSkills(CurrentActor actor, List<SkillRequest> incoming) {
        Long userId = actor.userId();
        List<Skill> existing = skillMapper.selectByUserId(userId);
        Map<Long, Skill> owned = indexById(existing, Skill::getId);
        Set<Long> incomingIds = incomingIds(incoming, SkillRequest::getId);
        for (Skill item : existing) {
            if (!incomingIds.contains(item.getId())) {
                skillService.deleteOwned(item.getId());
            }
        }
        for (SkillRequest dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                skillService.create(actor, dto);
            } else {
                Skill current = owned.get(dto.getId());
                if (current == null) {
                    current = requireOwned(dto.getId(), userId, skillMapper::selectById, Skill::getUserId,
                            ErrorCode.SKILL_NOT_FOUND);
                }
                skillService.updateOwned(current, dto);
            }
        }
    }

    /**
     * 同步教育经历。已加载记录直接更新；编号不在其中时再按编号确认归属。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param incoming 教育经历请求
     */
    private void syncEducations(CurrentActor actor, List<EducationRequest> incoming) {
        Long userId = actor.userId();
        List<Education> existing = educationMapper.selectByUserId(userId);
        Map<Long, Education> owned = indexById(existing, Education::getId);
        Set<Long> incomingIds = incomingIds(incoming, EducationRequest::getId);
        for (Education item : existing) {
            if (!incomingIds.contains(item.getId())) {
                educationService.deleteOwned(item.getId());
            }
        }
        for (EducationRequest dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                educationService.create(actor, dto);
            } else {
                Education current = owned.get(dto.getId());
                if (current == null) {
                    current = requireOwned(dto.getId(), userId, educationMapper::selectById, Education::getUserId,
                            ErrorCode.EDUCATION_NOT_FOUND);
                }
                educationService.updateOwned(current, dto);
            }
        }
    }

    private void syncCareers(CurrentActor actor, List<CareerRequest> incoming) {
        Long userId = actor.userId();
        List<Career> existing = careerMapper.selectByUserId(userId);
        Map<Long, Career> owned = indexById(existing, Career::getId);
        Set<Long> incomingIds = incomingIds(incoming, CareerRequest::getId);
        for (Career item : existing) {
            if (!incomingIds.contains(item.getId())) {
                careerService.deleteOwned(item.getId());
            }
        }
        for (CareerRequest dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                careerService.create(actor, dto);
            } else {
                Career current = owned.get(dto.getId());
                if (current == null) {
                    current = requireOwned(dto.getId(), userId, careerMapper::selectById, Career::getUserId,
                            ErrorCode.CAREER_NOT_FOUND);
                }
                careerService.updateOwned(current, dto);
            }
        }
    }

    private void syncProjects(CurrentActor actor, List<ProjectExperienceRequest> incoming) {
        Long userId = actor.userId();
        List<ProjectExperience> existing = projectExperienceMapper.selectByUserId(userId);
        Map<Long, ProjectExperience> owned = indexById(existing, ProjectExperience::getId);
        Set<Long> incomingIds = incomingIds(incoming, ProjectExperienceRequest::getId);
        for (ProjectExperience item : existing) {
            if (!incomingIds.contains(item.getId())) {
                projectExperienceService.deleteOwned(item.getId());
            }
        }
        for (ProjectExperienceRequest dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                projectExperienceService.create(actor, dto);
            } else {
                ProjectExperience current = owned.get(dto.getId());
                if (current == null) {
                    current = requireOwned(dto.getId(), userId, projectExperienceMapper::selectById,
                            ProjectExperience::getUserId, ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
                }
                projectExperienceService.updateOwned(current, dto);
            }
        }
    }

    private void syncAwards(CurrentActor actor, List<AwardRequest> incoming) {
        Long userId = actor.userId();
        List<Award> existing = awardMapper.selectByUserId(userId);
        Map<Long, Award> owned = indexById(existing, Award::getId);
        Set<Long> incomingIds = incomingIds(incoming, AwardRequest::getId);
        for (Award item : existing) {
            if (!incomingIds.contains(item.getId())) {
                awardService.deleteOwned(item.getId());
            }
        }
        for (AwardRequest dto : incoming) {
            validateForSave(dto, CreateGroup.class);
            if (dto.getId() == null) {
                awardService.create(actor, dto);
            } else {
                Award current = owned.get(dto.getId());
                if (current == null) {
                    current = requireOwned(dto.getId(), userId, awardMapper::selectById, Award::getUserId,
                            ErrorCode.AWARD_NOT_FOUND);
                }
                awardService.updateOwned(current, dto);
            }
        }
    }

    private <T> Map<Long, T> indexById(List<T> existing, Function<T, Long> id) {
        return existing.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private <T> Set<Long> incomingIds(List<T> incoming, Function<T, Long> id) {
        return incoming.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
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

    /**
     * 确认记录存在且属于当前用户。不存在时抛出调用方给出的错误码，归属不符时拒绝访问。
     *
     * @param id 记录编号
     * @param userId 当前用户编号
     * @param findById 按编号读取记录
     * @param ownerId 读取记录所属用户
     * @param notFound 记录不存在时的错误码
     * @return 属于当前用户的记录
     */
    private <T> T requireOwned(Long id, Long userId, Function<Long, T> findById, Function<T, Long> ownerId,
            ErrorCode notFound) {
        T item = findById.apply(id);
        if (item == null) {
            throw new BusinessException(notFound);
        }
        if (!ownerId.apply(item).equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return item;
    }

    /**
     * 解析导入的简历
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 导入请求
     * @return 解析后的简历
     */
    @Override
    public ResumeImportParseResponse parseImport(CurrentActor actor, ResumeImportParseRequest request) {
        java.util.Objects.requireNonNull(actor, "actor 不能为空");
        log.info("开始识别简历结构化字段");

        String sourceText = PdfParserUtil.normalizeExtractedText(resolveImportSourceText(request));

        ResumeImportParseResponse parsed;
        try {
            parsed = modelGateway.generateStructured(
                    actor,
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
     * 从上传文件提取文本后识别简历。文件流在提取结束后关闭，文件内容不落库。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param file 非空简历文件
     * @return 识别结果
     * @throws BusinessException 文件为空、无法读取、提取文本为空或识别失败
     */
    @Override
    public ResumeImportParseResponse parseImport(CurrentActor actor, MultipartFile file) {
        java.util.Objects.requireNonNull(actor, "actor 不能为空");
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        log.info("简历识别：从上传文件提取文本");
        String extracted;
        try (InputStream input = file.getInputStream()) {
            extracted = pdfParserUtil.extractText(input);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, exception);
        }
        if (!StringUtils.hasText(extracted)) {
            throw new BusinessException(ErrorCode.RESUME_IMPORT_TEXT_EMPTY);
        }
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText(extracted);
        return parseImport(actor, request);
    }

    /**
     * 解析导入的简历文本。
     *
     * @param request 纯文本请求
     * @return 去除首尾空白后的文本
     * @throws BusinessException 未提供文本或文本为空白
     */
    private String resolveImportSourceText(ResumeImportParseRequest request) {
        if (request.getRawText() != null) {
            String text = request.getRawText().trim();
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(ErrorCode.RESUME_IMPORT_TEXT_EMPTY);
            }
            log.info("简历识别：使用纯文本");
            return text;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必须提供简历文本或简历文件");
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

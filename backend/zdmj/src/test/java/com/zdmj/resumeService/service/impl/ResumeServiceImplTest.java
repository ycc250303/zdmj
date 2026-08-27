package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.ModelEnum;
import com.zdmj.common.ai.UserLlmRouter;
import com.zdmj.common.ai.prompt.PromptNames;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumeRequest;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.resumeService.entity.Resume;
import com.zdmj.resumeService.dto.ResumeResponse;
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
import com.zdmj.resumeService.service.SkillService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private EducationMapper educationMapper;
    @Mock
    private ProjectExperienceMapper projectExperienceMapper;
    @Mock
    private CareerMapper careerMapper;
    @Mock
    private AwardMapper awardMapper;
    @Mock
    private SkillMapper skillMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ChatUtil chatUtil;
    @Mock
    private UserLlmRouter userLlmRouter;
    @Mock
    private EducationService educationService;
    @Mock
    private CareerService careerService;
    @Mock
    private AwardService awardService;
    @Mock
    private ProjectExperienceService projectExperienceService;
    @Mock
    private SkillService skillService;
    @Mock
    private Validator validator;
    @Mock
    private PdfParserUtil pdfParserUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ResumeServiceImpl resumeService;

    @BeforeEach
    void setUp() {
        resumeService = spy(new ResumeServiceImpl(
                educationMapper, projectExperienceMapper, careerMapper, awardMapper, skillMapper, userMapper, chatUtil,
                userLlmRouter, objectMapper,
                educationService, careerService, awardService, projectExperienceService, skillService, validator,
                pdfParserUtil));
        ReflectionTestUtils.setField(Objects.requireNonNull(resumeService), "baseMapper", resumeMapper);
        lenient().doReturn(ModelEnum.DEEPSEEK_FLASH).when(userLlmRouter).resolveResumeImportModel();
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void create_userAlreadyHasResume_shouldThrow() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeRequest dto = new ResumeRequest();
        dto.setSkillId(99L);
        doReturn(true).when(resumeMapper).existsByUserId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.create(dto));

        assertEquals(ErrorCode.RESUME_ALREADY_EXISTS.getCode(), ex.getCode());
        verify(resumeService, never()).save(any(Resume.class));
    }

    @Test
    void create_success_shouldSaveResumeShell() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeRequest dto = new ResumeRequest();
        dto.setSkillId(99L);
        doReturn(false).when(resumeMapper).existsByUserId(1L);
        doReturn(true).when(resumeService).save(any(Resume.class));

        ResumeResponse out = resumeService.create(dto);

        assertEquals(1L, out.getUserId());
        assertEquals(99L, out.getSkillId());
        verify(resumeMapper).existsByUserId(1L);
        verify(resumeService).save(any(Resume.class));
    }

    @Test
    void create_saveFailed_shouldThrowCreateFailed() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeRequest dto = new ResumeRequest();
        dto.setSkillId(99L);
        doReturn(false).when(resumeMapper).existsByUserId(1L);
        doReturn(false).when(resumeService).save(any(Resume.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.create(dto));

        assertEquals(ErrorCode.RESUME_CREATE_FAILED.getCode(), ex.getCode());
        verify(resumeService).save(any(Resume.class));
    }

    @Test
    void getByUserId_success_shouldReturnMapperResult() {
        UserHolder.set(UserContext.of(1L, "u1"));
        doReturn(List.of(new Resume())).when(resumeMapper).selectByUserId(1L);

        List<ResumeResponse> out = resumeService.getByUserId();

        assertEquals(1, out.size());
        verify(resumeMapper).selectByUserId(1L);
    }

    @Test
    void update_notOwner_shouldThrowAndSkipUpdateById() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeRequest dto = new ResumeRequest();
        dto.setId(10L);
        dto.setSkillId(3L);
        Resume existing = new Resume();
        existing.setId(10L);
        existing.setUserId(2L);
        doReturn(existing).when(resumeMapper).selectById(10L);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.update(dto));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(resumeMapper).selectById(10L);
        verify(resumeService, never()).updateById(any(Resume.class));
    }

    @Test
    void update_success_shouldPersistShellFields() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeRequest dto = new ResumeRequest();
        dto.setId(10L);
        dto.setSkillId(5L);
        Resume existing = new Resume();
        existing.setId(10L);
        existing.setUserId(1L);
        doReturn(existing).when(resumeMapper).selectById(10L);
        doReturn(true).when(resumeService).updateById(any(Resume.class));

        ResumeResponse out = resumeService.update(dto);

        assertEquals(5L, out.getSkillId());
        verify(resumeService).updateById(existing);
    }

    @Test
    void update_updateFailed_shouldThrowUpdateFailed() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeRequest dto = new ResumeRequest();
        dto.setId(10L);
        dto.setSkillId(5L);
        Resume existing = new Resume();
        existing.setId(10L);
        existing.setUserId(1L);
        doReturn(existing).when(resumeMapper).selectById(10L);
        doReturn(false).when(resumeService).updateById(any(Resume.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.update(dto));

        assertEquals(ErrorCode.RESUME_UPDATE_FAILED.getCode(), ex.getCode());
        verify(resumeService).updateById(existing);
    }

    @Test
    void delete_removeFail_shouldThrowDeleteFailed() {
        UserHolder.set(UserContext.of(1L, "u1"));
        Resume existing = new Resume();
        existing.setId(11L);
        existing.setUserId(1L);
        doReturn(existing).when(resumeMapper).selectById(11L);
        doReturn(false).when(resumeService).removeById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.delete(11L));

        assertEquals(ErrorCode.RESUME_DELETE_FAILED.getCode(), ex.getCode());
        verify(resumeService).removeById(11L);
    }

    @Test
    void delete_noPermission_shouldThrowAndSkipRemove() {
        UserHolder.set(UserContext.of(1L, "u1"));
        Resume existing = new Resume();
        existing.setId(11L);
        existing.setUserId(2L);
        doReturn(existing).when(resumeMapper).selectById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.delete(11L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(resumeService, never()).removeById(11L);
    }

    @Test
    void delete_notFound_shouldThrowNotFound() {
        UserHolder.set(UserContext.of(1L, "u1"));
        doReturn(null).when(resumeMapper).selectById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.delete(11L));

        assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(), ex.getCode());
        verify(resumeService, never()).removeById(11L);
    }

    @Test
    void getResumeContentList_shouldReturnSingleItemWhenResumeExists() {
        UserHolder.set(UserContext.of(1L, "u1"));
        Resume resume = new Resume();
        resume.setId(31L);
        resume.setUserId(1L);
        doReturn(resume).when(resumeMapper).selectOneByUserId(1L);
        doReturn(List.of()).when(educationMapper).selectByUserId(1L);
        doReturn(List.of()).when(careerMapper).selectByUserId(1L);
        doReturn(List.of()).when(projectExperienceMapper).selectByUserId(1L);

        List<ResumeContentResponse> out = resumeService.getResumeContentList();

        assertEquals(1, out.size());
        assertEquals(31L, out.get(0).getId());
        verify(resumeMapper).selectOneByUserId(1L);
    }

    @Test
    void getResumeContentList_noResume_shouldReturnEmpty() {
        UserHolder.set(UserContext.of(1L, "u1"));
        doReturn(null).when(resumeMapper).selectOneByUserId(1L);

        List<ResumeContentResponse> out = resumeService.getResumeContentList();

        assertEquals(0, out.size());
    }

    @Test
    void getByUserId_userNotLogin_shouldThrowAndSkipMapperCall() {
        UserHolder.clear();

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.getByUserId());

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(resumeMapper, never()).selectByUserId(any());
    }

    @Test
    void parseImport_missingInput_shouldThrowValidationError() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.parseImport(request));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(chatUtil, never()).chatStructuredOnceWithPlatformModel(any(), any(), any(), any(), any());
    }

    @Test
    void parseImport_blankRawText_shouldThrowTextEmpty() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("   ");

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.parseImport(request));

        assertEquals(ErrorCode.RESUME_IMPORT_TEXT_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void parseImport_llmFailure_shouldThrowParseFailed() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("张三 某某大学 软件工程");
        doThrow(new RuntimeException("llm down")).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.parseImport(request));

        assertEquals(ErrorCode.RESUME_IMPORT_PARSE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void parseImport_success_shouldNormalizeAndUseDeepSeekFlash() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume body");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.EducationItem edu = new ResumeImportParseResponse.EducationItem();
        edu.setSchool("SCU");
        edu.setMajor("SE");
        edu.setDegree(99);
        edu.setStartDate("2020-9");
        edu.setEndDate("至今");
        llmResult.setEducations(List.of(edu));

        ResumeImportParseResponse.CareerItem blankCareer = new ResumeImportParseResponse.CareerItem();
        llmResult.setCareers(List.of(blankCareer));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResponse out = resumeService.parseImport(request);

        assertEquals(1, out.getEducations().size());
        assertEquals("SCU", out.getEducations().get(0).getSchool());
        assertEquals(6, out.getEducations().get(0).getDegree());
        assertEquals("2020-09-01", out.getEducations().get(0).getStartDate());
        assertEquals(null, out.getEducations().get(0).getEndDate());
        assertEquals(0, out.getCareers().size());
    }

    @Test
    void parseImport_yearOnlyDate_shouldNormalizeToNull() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.EducationItem edu = new ResumeImportParseResponse.EducationItem();
        edu.setSchool("SCU");
        edu.setStartDate("2020");
        edu.setEndDate("2022年");
        llmResult.setEducations(List.of(edu));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResponse out = resumeService.parseImport(request);

        assertEquals(null, out.getEducations().get(0).getStartDate());
        assertEquals(null, out.getEducations().get(0).getEndDate());
    }

    @Test
    void parseImport_chineseYearMonth_shouldUseFirstDay() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.CareerItem career = new ResumeImportParseResponse.CareerItem();
        career.setCompany("ACME");
        career.setStartDate("2021年3月");
        llmResult.setCareers(List.of(career));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResponse out = resumeService.parseImport(request);

        assertEquals("2021-03-01", out.getCareers().get(0).getStartDate());
    }

    @Test
    void parseImport_awardYearOnlyInName_shouldNormalizeAward() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.AwardItem award = new ResumeImportParseResponse.AwardItem();
        award.setName("2024年同济大学本科生奖学金");
        llmResult.setAwards(List.of(award));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResponse out = resumeService.parseImport(request);

        assertEquals(1, out.getAwards().size());
        assertEquals("2024-01-01", out.getAwards().get(0).getAwardDate());
        assertEquals(1, out.getAwards().get(0).getAwardType());
    }

    @Test
    void parseImport_scholarshipFromSourceText_shouldSupplementWhenLlmMisses() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("""
                获奖情况
                2024年同济大学本科生奖学金
                2023年全国大学生数学建模竞赛省一等奖
                """);

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.AwardItem competition = new ResumeImportParseResponse.AwardItem();
        competition.setName("全国大学生数学建模竞赛省一等奖");
        competition.setAwardType(2);
        competition.setAwardDate("2023-11-01");
        llmResult.setAwards(List.of(competition));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResponse out = resumeService.parseImport(request);

        assertEquals(2, out.getAwards().size());
        assertTrue(out.getAwards().stream().anyMatch(a ->
                a.getName().contains("本科生奖学金") && Integer.valueOf(1).equals(a.getAwardType())));
    }

    @Test
    void parseImport_awardMisclassifiedAsCompetition_shouldResolveToScholarship() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.AwardItem award = new ResumeImportParseResponse.AwardItem();
        award.setName("同济大学本科生奖学金");
        award.setAwardType(2);
        award.setAwardDate("2024-09-01");
        llmResult.setAwards(List.of(award));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResponse out = resumeService.parseImport(request);

        assertEquals(1, out.getAwards().size());
        assertEquals(1, out.getAwards().get(0).getAwardType());
    }

    @Test
    void parseImport_awardFromProjectHighlight_shouldSupplement() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.ProjectItem project = new ResumeImportParseResponse.ProjectItem();
        project.setName("CoEdit");
        project.setRole("负责人");
        project.setStartDate("2025-05-01");
        project.setHighlights(List.of("该项目获 2025 年中国高校计算机大赛智能交互创新赛全国一等奖"));
        llmResult.setProjects(List.of(project));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResponse out = resumeService.parseImport(request);

        assertEquals(1, out.getAwards().size());
        assertTrue(out.getAwards().get(0).getName().contains("一等奖"));
        assertEquals("2025-01-01", out.getAwards().get(0).getAwardDate());
    }

    @Test
    void parseImport_projectHighlightsString_shouldNormalizeToJsonArray() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.ProjectItem project = new ResumeImportParseResponse.ProjectItem();
        project.setName("Demo");
        project.setRole("开发");
        project.setStartDate("2025-01-01");
        project.setHighlights("该项目获全国一等奖");
        llmResult.setProjects(List.of(project));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResponse out = resumeService.parseImport(request);

        assertEquals(1, out.getProjects().size());
        assertTrue(String.valueOf(out.getProjects().get(0).getHighlights()).startsWith("["));
    }

    @Test
    void parseImport_longText_shouldTruncateAndAddWarning() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("a".repeat(16000));

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResponse.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResponse out = resumeService.parseImport(request);

        assertNotNull(out.getWarnings());
        assertEquals(true, out.getWarnings().stream().anyMatch(w -> w.contains("截断")));
    }

    @Test
    void saveMyResumeContent_shouldSyncSkillAndPersistShell() {
        UserHolder.set(UserContext.of(1L, "u1"));
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(1L);
        resume.setSkillId(9L);
        doReturn(resume).when(resumeMapper).selectOneByUserId(1L);
        doReturn(List.of()).when(educationMapper).selectByUserId(1L);
        doReturn(List.of()).when(careerMapper).selectByUserId(1L);
        doReturn(List.of()).when(projectExperienceMapper).selectByUserId(1L);
        doReturn(List.of()).when(awardMapper).selectByUserId(1L);
        doReturn(true).when(resumeService).updateById(any(Resume.class));

        var request = new com.zdmj.resumeService.dto.ResumeContentSaveRequest();
        var skill = new com.zdmj.resumeService.dto.SkillRequest();
        skill.setId(9L);
        skill.setContent(List.of());
        request.setSkill(skill);
        request.setEducations(List.of());
        request.setCareers(List.of());
        request.setProjects(List.of());
        request.setAwards(List.of());

        ResumeContentResponse out = resumeService.saveMyResumeContent(request);

        assertEquals(1L, out.getId());
        verify(skillService).update(any());
        verify(resumeService).updateById(resume);
    }
}

package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumeContentSaveRequest;
import com.zdmj.resumeService.dto.ResumePersonalInfoDTO;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import com.zdmj.resumeService.dto.SkillRequest;
import com.zdmj.resumeService.entity.Skill;
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
import com.zdmj.resumeService.service.SkillService;
import jakarta.validation.Validator;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    private final CurrentActor actor = new CurrentActor(1L);

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
    private UserLoginService userService;
    @Mock
    private ModelGateway modelGateway;
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

    private ResumeServiceImpl resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeServiceImpl(
                educationMapper, projectExperienceMapper, careerMapper, awardMapper, skillMapper, resumeMapper,
                userService, modelGateway, educationService, careerService, awardService, projectExperienceService,
                skillService, validator, pdfParserUtil, Jackson2ObjectMapperBuilder.json().build());
    }

    @Test
    void getResumeContentList_shouldReturnSingleItemWhenResumeExists() {
        ResumeContentRow row = emptyContent();
        row.setSkills("[{\"id\":9,\"name\":\"Java\"}]");
        doReturn(row).when(resumeMapper).selectContentByUserId(1L);

        List<ResumeContentResponse> out = resumeService.getResumeContentList(actor);

        assertEquals(1, out.size());
        assertEquals(9L, out.get(0).getSkills().get(0).getId());
        assertEquals("Java", out.get(0).getSkills().get(0).getName());
        verify(educationMapper, never()).selectByUserId(any());
    }

    @Test
    void getResumeContentList_noResume_shouldReturnEmpty() {
        doReturn(emptyContent()).when(resumeMapper).selectContentByUserId(1L);

        List<ResumeContentResponse> out = resumeService.getResumeContentList(actor);

        assertEquals(0, out.size());
    }

    @Test
    void getMyResumeContent_shouldMapSingleQueryRow() {
        ResumeContentRow row = emptyContent();
        row.setSkills("[{\"id\":9,\"name\":\"Java\",\"description\":\"熟悉 Spring\"}]");
        row.setPersonalName("张三");
        row.setPersonalPhone("13800000000");
        row.setPersonalWebsite("https://example.com");
        row.setPreferredWorkCity("上海");
        row.setEducations("""
                [{"id":1,"school":"测试大学","major":"计算机","degree":3,"startDate":"2020-09-01","endDate":"2024-06-30","gpa":"3.8"}]""");
        row.setProjects("""
                [{"id":2,"name":"测试项目","startDate":"2023-01-01","role":"开发","description":"实现接口","contribution":"完成后端","techStack":["Java"],"highlights":[]}]""");
        row.setAwards("""
                [{"id":3,"name":"测试奖项","awardDate":"2024-05-01"}]""");
        doReturn(row).when(resumeMapper).selectContentByUserId(1L);

        ResumeContentResponse out = resumeService.getMyResumeContent(actor);

        assertEquals(9L, out.getSkills().get(0).getId());
        assertEquals("Java", out.getSkills().get(0).getName());
        assertEquals("熟悉 Spring", out.getSkills().get(0).getDescription());
        assertEquals("测试大学", out.getEducations().get(0).getSchool());
        assertEquals(LocalDate.of(2020, 9, 1), out.getEducations().get(0).getStartDate());
        assertEquals(3, out.getEducations().get(0).getDegree());
        assertEquals("测试项目", out.getProjects().get(0).getName());
        assertEquals(List.of("Java"), out.getProjects().get(0).getTechStack());
        assertEquals(LocalDate.of(2024, 5, 1), out.getAwards().get(0).getAwardDate());
        assertEquals("张三", out.getPersonalInfo().getName());
        assertEquals("https://example.com", out.getPersonalInfo().getHomepageUrl());
        assertEquals("上海", out.getPersonalInfo().getPreferredWorkCity());
        verify(educationMapper, never()).selectByUserId(any());
    }

    @Test
    void parseImport_missingInput_shouldThrowValidationError() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.parseImport(actor, request));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(modelGateway, never()).generateStructured(any(), any(), any());
    }

    @Test
    void parseImport_blankRawText_shouldThrowTextEmpty() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("   ");

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.parseImport(actor, request));

        assertEquals(ErrorCode.RESUME_IMPORT_TEXT_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void parseImport_llmFailure_shouldThrowParseFailed() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("张三 某某大学 软件工程");
        doThrow(new RuntimeException("llm down")).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.parseImport(actor, request));

        assertEquals(ErrorCode.RESUME_IMPORT_PARSE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void parseImport_success_shouldNormalizeAndUseDeepSeekFlash() {
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

        doReturn(llmResult).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        ResumeImportParseResponse out = resumeService.parseImport(actor, request);

        assertEquals(1, out.getEducations().size());
        assertEquals("SCU", out.getEducations().get(0).getSchool());
        assertEquals(6, out.getEducations().get(0).getDegree());
        assertEquals("2020-09-01", out.getEducations().get(0).getStartDate());
        assertEquals(null, out.getEducations().get(0).getEndDate());
        assertEquals(0, out.getCareers().size());
    }

    @Test
    void parseImport_yearOnlyDate_shouldNormalizeToNull() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.EducationItem edu = new ResumeImportParseResponse.EducationItem();
        edu.setSchool("SCU");
        edu.setStartDate("2020");
        edu.setEndDate("2022年");
        llmResult.setEducations(List.of(edu));

        doReturn(llmResult).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        ResumeImportParseResponse out = resumeService.parseImport(actor, request);

        assertEquals(null, out.getEducations().get(0).getStartDate());
        assertEquals(null, out.getEducations().get(0).getEndDate());
    }

    @Test
    void parseImport_chineseYearMonth_shouldUseFirstDay() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.CareerItem career = new ResumeImportParseResponse.CareerItem();
        career.setCompany("ACME");
        career.setStartDate("2021年3月");
        llmResult.setCareers(List.of(career));

        doReturn(llmResult).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        ResumeImportParseResponse out = resumeService.parseImport(actor, request);

        assertEquals("2021-03-01", out.getCareers().get(0).getStartDate());
    }

    @Test
    void parseImport_awardYearOnlyInName_shouldNormalizeAward() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("2024年同济大学本科生奖学金");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.AwardItem award = new ResumeImportParseResponse.AwardItem();
        award.setName("2024年同济大学本科生奖学金");
        llmResult.setAwards(List.of(award));

        doReturn(llmResult).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        ResumeImportParseResponse out = resumeService.parseImport(actor, request);

        assertEquals(1, out.getAwards().size());
        assertEquals("2024年同济大学本科生奖学金", out.getAwards().get(0).getName());
        assertEquals("2024-01-01", out.getAwards().get(0).getAwardDate());
        verify(modelGateway, times(1)).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));
    }

    @Test
    void parseImport_shouldKeepAwardsFromSingleParseCall() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("""
                GPA: 4.43/5.00，获同济大学本科优秀学生奖学金二等奖
                以项目负责人身份获 2025 年中国高校计算机大赛智能交互创新赛全国一等奖、AIGC 创新赛全国三等奖
                """);

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.AwardItem scholarship = new ResumeImportParseResponse.AwardItem();
        scholarship.setName("同济大学本科优秀学生奖学金二等奖");
        scholarship.setAwardDate("2025-01-01");
        ResumeImportParseResponse.AwardItem first = new ResumeImportParseResponse.AwardItem();
        first.setName("2025年中国高校计算机大赛智能交互创新赛全国一等奖");
        first.setAwardDate("2025-01-01");
        ResumeImportParseResponse.AwardItem second = new ResumeImportParseResponse.AwardItem();
        second.setName("AIGC创新赛全国三等奖");
        second.setAwardDate("2025-01-01");
        llmResult.setAwards(List.of(scholarship, first, second));

        doReturn(llmResult).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        ResumeImportParseResponse out = resumeService.parseImport(actor, request);

        ArgumentCaptor<ModelRequest> parseMsg =
                ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelGateway).generateStructured(any(), parseMsg.capture(), eq(ResumeImportParseResponse.class));
        assertTrue(parseMsg.getValue().message().contains("GPA: 4.43/5.00"));
        assertEquals("resume-import-parse", parseMsg.getValue().promptName());
        assertEquals(3, out.getAwards().size());
        assertEquals(1, out.getAwards().stream().filter(a -> a.getName().contains("优秀学生奖学金")).count());
    }

    @Test
    void parseImport_llmMissesScholarship_shouldNotRegexFillFromSource() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("""
                获奖情况
                2024年同济大学本科生奖学金
                2023年全国大学生数学建模竞赛省一等奖
                """);

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.AwardItem competition = new ResumeImportParseResponse.AwardItem();
        competition.setName("全国大学生数学建模竞赛省一等奖");
        competition.setAwardDate("2023-11-01");
        llmResult.setAwards(List.of(competition));

        doReturn(llmResult).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        ResumeImportParseResponse out = resumeService.parseImport(actor, request);

        assertEquals(1, out.getAwards().size());
        assertTrue(out.getAwards().get(0).getName().contains("数学建模"));
        verify(modelGateway, times(1)).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));
    }

    @Test
    void parseImport_emptyAwardsFromLlm_shouldKeepEmptyList() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume body");

        doReturn(new ResumeImportParseResponse()).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        ResumeImportParseResponse out = resumeService.parseImport(actor, request);

        assertEquals(0, out.getAwards().size());
        verify(modelGateway, times(1)).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));
    }

    @Test
    void parseImport_projectDescriptionMentioningAward_shouldNotInventAward() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        ResumeImportParseResponse.ProjectItem project = new ResumeImportParseResponse.ProjectItem();
        project.setName("CoEdit");
        project.setRole("负责人");
        project.setStartDate("2025-05-01");
        project.setDescription("该项目获 2025 年中国高校计算机大赛智能交互创新赛全国一等奖");
        llmResult.setProjects(List.of(project));

        doReturn(llmResult).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        ResumeImportParseResponse out = resumeService.parseImport(actor, request);

        assertEquals(0, out.getAwards().size());
        assertEquals(1, out.getProjects().size());
    }

    @Test
    void parseImport_shouldNotTruncateLongRawText() {
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        String raw = "a".repeat(16000);
        request.setRawText(raw);

        ResumeImportParseResponse llmResult = new ResumeImportParseResponse();
        doReturn(llmResult).when(modelGateway).generateStructured(any(), argThat(ResumeServiceImplTest::resumeImportRequest), eq(ResumeImportParseResponse.class));

        ResumeImportParseResponse out = resumeService.parseImport(actor, request);

        ArgumentCaptor<ModelRequest> parseMsg =
                ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelGateway).generateStructured(any(), parseMsg.capture(), eq(ResumeImportParseResponse.class));
        assertEquals(16000, parseMsg.getValue().message().length());
        assertEquals("resume-import-parse", parseMsg.getValue().promptName());
        assertEquals(true, out.getWarnings() == null || out.getWarnings().stream().noneMatch(w -> w.contains("截断")));
    }

    @Test
    void saveMyResumeContent_shouldSyncSkill() {
        doReturn(List.of()).when(educationMapper).selectByUserId(1L);
        doReturn(List.of()).when(careerMapper).selectByUserId(1L);
        doReturn(List.of()).when(projectExperienceMapper).selectByUserId(1L);
        doReturn(List.of()).when(awardMapper).selectByUserId(1L);
        Skill existingSkill = new Skill();
        existingSkill.setId(9L);
        existingSkill.setUserId(1L);
        doReturn(List.of(existingSkill)).when(skillMapper).selectByUserId(1L);
        doReturn(java.util.Set.of()).when(validator).validate(any(), any(Class[].class));
        doReturn(emptyContent()).when(resumeMapper).selectContentByUserId(1L);

        var request = new com.zdmj.resumeService.dto.ResumeContentSaveRequest();
        var skill = new com.zdmj.resumeService.dto.SkillRequest();
        skill.setId(9L);
        skill.setName("Java");
        request.setSkills(List.of(skill));
        request.setEducations(List.of());
        request.setCareers(List.of());
        request.setProjects(List.of());
        request.setAwards(List.of());

        resumeService.saveMyResumeContent(actor, request);

        verify(skillService).updateOwned(eq(existingSkill), any());
        verify(skillMapper, never()).selectById(any());
        verify(userService, never()).updateProfile(eq(actor), any(), any());
    }

    @Test
    void saveMyResumeContent_duplicateSkillId_shouldUpdateEachSubmission() {
        doReturn(List.of()).when(educationMapper).selectByUserId(1L);
        doReturn(List.of()).when(careerMapper).selectByUserId(1L);
        doReturn(List.of()).when(projectExperienceMapper).selectByUserId(1L);
        doReturn(List.of()).when(awardMapper).selectByUserId(1L);
        Skill existingSkill = new Skill();
        existingSkill.setId(9L);
        existingSkill.setUserId(1L);
        doReturn(List.of(existingSkill)).when(skillMapper).selectByUserId(1L);
        doReturn(java.util.Set.of()).when(validator).validate(any(), any(Class[].class));
        doReturn(emptyContent()).when(resumeMapper).selectContentByUserId(1L);

        SkillRequest first = new SkillRequest();
        first.setId(9L);
        first.setName("Java");
        SkillRequest second = new SkillRequest();
        second.setId(9L);
        second.setName("Python");
        ResumeContentSaveRequest request = new ResumeContentSaveRequest();
        request.setSkills(List.of(first, second));
        request.setEducations(List.of());
        request.setCareers(List.of());
        request.setProjects(List.of());
        request.setAwards(List.of());

        resumeService.saveMyResumeContent(actor, request);

        verify(skillService, times(2)).updateOwned(eq(existingSkill), any());
        verify(skillMapper, never()).selectById(any());
        verify(skillService, never()).deleteOwned(any());
    }

    @Test
    void saveMyResumeContent_emptySkills_shouldDeleteExisting() {
        doReturn(List.of()).when(educationMapper).selectByUserId(1L);
        doReturn(List.of()).when(careerMapper).selectByUserId(1L);
        doReturn(List.of()).when(projectExperienceMapper).selectByUserId(1L);
        doReturn(List.of()).when(awardMapper).selectByUserId(1L);
        Skill existingSkill = new Skill();
        existingSkill.setId(9L);
        existingSkill.setUserId(1L);
        doReturn(List.of(existingSkill)).when(skillMapper).selectByUserId(1L);
        doReturn(emptyContent()).when(resumeMapper).selectContentByUserId(1L);

        ResumeContentSaveRequest request = new ResumeContentSaveRequest();
        request.setSkills(List.of());
        request.setEducations(List.of());
        request.setCareers(List.of());
        request.setProjects(List.of());
        request.setAwards(List.of());

        resumeService.saveMyResumeContent(actor, request);

        verify(skillService).deleteOwned(9L);
        verify(skillMapper, never()).selectById(any());
        verify(skillService, never()).create(eq(actor), any());
        verify(skillService, never()).updateOwned(any(), any());
    }

    @Test
    void saveMyResumeContent_shouldUpdatePersonalInfoThroughUserService() {
        doReturn(List.of()).when(educationMapper).selectByUserId(1L);
        doReturn(List.of()).when(careerMapper).selectByUserId(1L);
        doReturn(List.of()).when(projectExperienceMapper).selectByUserId(1L);
        doReturn(List.of()).when(awardMapper).selectByUserId(1L);
        Skill existingSkill = new Skill();
        existingSkill.setId(9L);
        existingSkill.setUserId(1L);
        doReturn(List.of(existingSkill)).when(skillMapper).selectByUserId(1L);
        doReturn(java.util.Set.of()).when(validator).validate(any(), any(Class[].class));
        doReturn(emptyContent()).when(resumeMapper).selectContentByUserId(1L);

        ResumePersonalInfoDTO personalInfo = new ResumePersonalInfoDTO();
        personalInfo.setName("  张三  ");
        personalInfo.setPhone("");
        personalInfo.setHomepageUrl(" https://example.com ");
        personalInfo.setPreferredWorkCity(" 上海 ");
        ResumeContentSaveRequest request = new ResumeContentSaveRequest();
        SkillRequest skill = new SkillRequest();
        skill.setId(9L);
        skill.setName("Java");
        request.setSkills(List.of(skill));
        request.setEducations(List.of());
        request.setCareers(List.of());
        request.setProjects(List.of());
        request.setAwards(List.of());
        request.setPersonalInfo(personalInfo);

        resumeService.saveMyResumeContent(actor, request);

        ArgumentCaptor<UserUpdateRequest> update = ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(userService).updateProfile(eq(actor), eq(1L), update.capture());
        assertEquals("  张三  ", update.getValue().getName());
        assertEquals("", update.getValue().getPhone());
        assertEquals(" https://example.com ", update.getValue().getWebsite());
        assertEquals(" 上海 ", update.getValue().getPreferredWorkCity());
        verify(skillMapper, never()).selectById(any());
    }

    @Test
    void saveMyResumeContent_foreignSkillId_shouldRejectWithoutWrite() {
        doReturn(List.of()).when(skillMapper).selectByUserId(1L);
        Skill foreign = new Skill();
        foreign.setId(9L);
        foreign.setUserId(2L);
        doReturn(foreign).when(skillMapper).selectById(9L);
        doReturn(java.util.Set.of()).when(validator).validate(any(), any(Class[].class));

        SkillRequest skill = new SkillRequest();
        skill.setId(9L);
        skill.setName("Java");
        ResumeContentSaveRequest request = new ResumeContentSaveRequest();
        request.setSkills(List.of(skill));
        request.setEducations(List.of());
        request.setCareers(List.of());
        request.setProjects(List.of());
        request.setAwards(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.saveMyResumeContent(actor, request));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(skillService, never()).updateOwned(any(), any());
    }

    @Test
    void saveMyResumeContent_missingSkillId_shouldRejectWithoutWrite() {
        doReturn(List.of()).when(skillMapper).selectByUserId(1L);
        doReturn(null).when(skillMapper).selectById(9L);
        doReturn(java.util.Set.of()).when(validator).validate(any(), any(Class[].class));

        SkillRequest skill = new SkillRequest();
        skill.setId(9L);
        skill.setName("Java");
        ResumeContentSaveRequest request = new ResumeContentSaveRequest();
        request.setSkills(List.of(skill));
        request.setEducations(List.of());
        request.setCareers(List.of());
        request.setProjects(List.of());
        request.setAwards(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.saveMyResumeContent(actor, request));

        assertEquals(ErrorCode.SKILL_NOT_FOUND.getCode(), ex.getCode());
        verify(skillService, never()).updateOwned(any(), any());
    }

    private static ResumeContentRow emptyContent() {
        ResumeContentRow row = new ResumeContentRow();
        row.setSkills("[]");
        row.setEducations("[]");
        row.setCareers("[]");
        row.setProjects("[]");
        row.setAwards("[]");
        return row;
    }

    private static boolean resumeImportRequest(ModelRequest request) {
        return "resume-import-parse".equals(request.promptName())
                && request.promptVars() == null;
    }
}

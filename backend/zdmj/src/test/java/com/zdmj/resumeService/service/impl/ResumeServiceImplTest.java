package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.ModelEnum;
import com.zdmj.common.ai.prompt.PromptNames;
import com.zdmj.resumeService.dto.ResumeContentDTO;
import com.zdmj.resumeService.dto.ResumeDTO;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.resumeService.entity.Career;
import com.zdmj.resumeService.entity.Education;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.entity.Resume;
import com.zdmj.resumeService.entity.Skill;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.mapper.EducationMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.mapper.ResumeMapper;
import com.zdmj.resumeService.mapper.SkillMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
    private SkillMapper skillMapper;
    @Mock
    private ChatUtil chatUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ResumeServiceImpl resumeService;

    @BeforeEach
    void setUp() {
        resumeService = spy(new ResumeServiceImpl(
                educationMapper, projectExperienceMapper, careerMapper, skillMapper, chatUtil, objectMapper));
        ReflectionTestUtils.setField(Objects.requireNonNull(resumeService), "baseMapper", resumeMapper);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void create_nameExists_shouldThrowAndSkipSave() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setName("same-name");
        dto.setSkillId(99L);
        doReturn(true).when(resumeMapper).existsByName(1L, "same-name", null);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.create(dto));

        assertEquals(ErrorCode.RESUME_NAME_EXISTS.getCode(), ex.getCode());
        verify(resumeMapper).existsByName(1L, "same-name", null);
        verify(resumeService, never()).save(any(Resume.class));
    }

    @Test
    void create_success_shouldAssembleAndSaveResume() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setName("java-backend");
        dto.setSkillId(99L);
        doReturn(false).when(resumeMapper).existsByName(1L, "java-backend", null);
        doReturn(List.of(101L, 102L)).when(educationMapper).selectEducationIds(1L, true);
        doReturn(List.of(201L)).when(careerMapper).selectCareerIds(1L, true);
        doReturn(List.of(301L)).when(projectExperienceMapper).selectProjectExperienceIds(1L, true);
        doReturn(true).when(resumeService).save(any(Resume.class));

        Resume out = resumeService.create(dto);

        assertEquals(1L, out.getUserId());
        assertEquals("java-backend", out.getName());
        assertEquals(2, out.getEducations().size());
        assertEquals(1, out.getCareers().size());
        assertEquals(1, out.getProjects().size());
        verify(resumeMapper).existsByName(1L, "java-backend", null);
        verify(educationMapper).selectEducationIds(1L, true);
        verify(careerMapper).selectCareerIds(1L, true);
        verify(projectExperienceMapper).selectProjectExperienceIds(1L, true);
        verify(resumeService).save(any(Resume.class));
    }

    @Test
    void create_saveFailed_shouldThrowCreateFailed() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setName("java-backend");
        doReturn(false).when(resumeMapper).existsByName(1L, "java-backend", null);
        doReturn(List.of()).when(educationMapper).selectEducationIds(1L, true);
        doReturn(List.of()).when(careerMapper).selectCareerIds(1L, true);
        doReturn(List.of()).when(projectExperienceMapper).selectProjectExperienceIds(1L, true);
        doReturn(false).when(resumeService).save(any(Resume.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.create(dto));

        assertEquals(ErrorCode.RESUME_CREATE_FAILED.getCode(), ex.getCode());
        verify(resumeService).save(any(Resume.class));
    }

    @Test
    void getById_notFound_shouldThrow() {
        doReturn(null).when(resumeMapper).selectById(404L);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.getById(404L));

        assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(), ex.getCode());
        verify(resumeMapper).selectById(404L);
    }

    @Test
    void getByUserId_success_shouldReturnMapperResult() {
        UserHolder.set(UserContext.of(1L, "u1"));
        doReturn(List.of(new Resume())).when(resumeMapper).selectByUserId(1L);

        List<Resume> out = resumeService.getByUserId();

        assertEquals(1, out.size());
        verify(resumeMapper).selectByUserId(1L);
    }

    @Test
    void update_notOwner_shouldThrowAndSkipUpdateById() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setId(10L);
        dto.setName("new");
        dto.setSkillId(3L);
        Resume existing = new Resume();
        existing.setId(10L);
        existing.setUserId(2L);
        existing.setName("old");
        doReturn(existing).when(resumeMapper).selectById(10L);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.update(dto));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(resumeMapper).selectById(10L);
        verify(resumeService, never()).updateById(any(Resume.class));
    }

    @Test
    void update_success_shouldRefreshVisibleCollectionsAndPersist() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setId(10L);
        dto.setName("new-name");
        dto.setSkillId(5L);
        Resume existing = new Resume();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("old-name");
        doReturn(existing).when(resumeMapper).selectById(10L);
        doReturn(false).when(resumeMapper).existsByName(1L, "new-name", 10L);
        doReturn(List.of(11L)).when(educationMapper).selectEducationIds(1L, true);
        doReturn(List.of(21L, 22L)).when(careerMapper).selectCareerIds(1L, true);
        doReturn(List.of(31L)).when(projectExperienceMapper).selectProjectExperienceIds(1L, true);
        doReturn(true).when(resumeService).updateById(any(Resume.class));

        Resume out = resumeService.update(dto);

        assertEquals("new-name", out.getName());
        assertEquals(5L, out.getSkillId());
        assertEquals(1, out.getEducations().size());
        assertEquals(2, out.getCareers().size());
        assertEquals(1, out.getProjects().size());
        verify(resumeMapper).existsByName(1L, "new-name", 10L);
        verify(educationMapper).selectEducationIds(1L, true);
        verify(careerMapper).selectCareerIds(1L, true);
        verify(projectExperienceMapper).selectProjectExperienceIds(1L, true);
        verify(resumeService).updateById(existing);
    }

    @Test
    void update_sameName_shouldSkipNameDupCheck() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setId(10L);
        dto.setName("same");
        dto.setSkillId(6L);
        Resume existing = new Resume();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("same");
        doReturn(existing).when(resumeMapper).selectById(10L);
        doReturn(List.of()).when(educationMapper).selectEducationIds(1L, true);
        doReturn(List.of()).when(careerMapper).selectCareerIds(1L, true);
        doReturn(List.of()).when(projectExperienceMapper).selectProjectExperienceIds(1L, true);
        doReturn(true).when(resumeService).updateById(any(Resume.class));

        resumeService.update(dto);

        verify(resumeMapper, never()).existsByName(any(), any(), any());
        verify(resumeService).updateById(existing);
    }

    @Test
    void update_nameChangedAndDuplicated_shouldThrowNameExists() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setId(10L);
        dto.setName("new-name");
        Resume existing = new Resume();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("old-name");
        doReturn(existing).when(resumeMapper).selectById(10L);
        doReturn(true).when(resumeMapper).existsByName(1L, "new-name", 10L);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.update(dto));

        assertEquals(ErrorCode.RESUME_NAME_EXISTS.getCode(), ex.getCode());
        verify(resumeService, never()).updateById(any(Resume.class));
    }

    @Test
    void update_updateFailed_shouldThrowUpdateFailed() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setId(10L);
        dto.setName("new-name");
        Resume existing = new Resume();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("old-name");
        doReturn(existing).when(resumeMapper).selectById(10L);
        doReturn(false).when(resumeMapper).existsByName(1L, "new-name", 10L);
        doReturn(List.of()).when(educationMapper).selectEducationIds(1L, true);
        doReturn(List.of()).when(careerMapper).selectCareerIds(1L, true);
        doReturn(List.of()).when(projectExperienceMapper).selectProjectExperienceIds(1L, true);
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
        existing.setName("r1");
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
        existing.setName("r1");
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
    void getResumeContentById_shouldMapNestedEntities() {
        UserHolder.set(UserContext.of(1L, "u1"));
        Resume resume = new Resume();
        resume.setId(20L);
        resume.setUserId(1L);
        resume.setName("backend-resume");
        resume.setSkillId(8L);
        doReturn(resume).when(resumeMapper).selectById(20L);

        Skill skill = new Skill();
        skill.setId(8L);
        skill.setName("skill-pack");
        skill.setContent(null);
        doReturn(skill).when(skillMapper).selectById(8L);

        Education education = new Education();
        education.setId(101L);
        education.setSchool("SCU");
        Career career = new Career();
        career.setId(102L);
        career.setCompany("ZDMJ");
        ProjectExperience project = new ProjectExperience();
        project.setId(103L);
        project.setName("AI Resume");
        doReturn(List.of(education)).when(educationMapper).selectByResumeId(20L);
        doReturn(List.of(career)).when(careerMapper).selectByResumeId(20L);
        doReturn(List.of(project)).when(projectExperienceMapper).selectByResumeId(20L);

        ResumeContentDTO out = resumeService.getResumeContentById(20L);

        assertEquals(20L, out.getId());
        assertEquals("backend-resume", out.getName());
        assertNotNull(out.getSkill());
        assertEquals("skill-pack", out.getSkill().getName());
        assertNotNull(out.getSkill().getContent());
        assertEquals(0, out.getSkill().getContent().size());
        assertEquals(1, out.getEducations().size());
        assertEquals("SCU", out.getEducations().get(0).getSchool());
        assertEquals(1, out.getCareers().size());
        assertEquals("ZDMJ", out.getCareers().get(0).getCompany());
        assertEquals(1, out.getProjects().size());
        assertEquals("AI Resume", out.getProjects().get(0).getName());
        verify(skillMapper).selectById(8L);
        verify(educationMapper).selectByResumeId(20L);
        verify(careerMapper).selectByResumeId(20L);
        verify(projectExperienceMapper).selectByResumeId(20L);
    }

    @Test
    void getResumeContentList_shouldAggregateByCurrentUserResumes() {
        UserHolder.set(UserContext.of(1L, "u1"));
        Resume r1 = new Resume();
        r1.setId(31L);
        Resume r2 = new Resume();
        r2.setId(32L);
        doReturn(List.of(r1, r2)).when(resumeMapper).selectByUserId(1L);

        ResumeContentDTO c1 = new ResumeContentDTO();
        c1.setId(31L);
        ResumeContentDTO c2 = new ResumeContentDTO();
        c2.setId(32L);
        doReturn(c1).when(resumeService).getResumeContentById(31L);
        doReturn(c2).when(resumeService).getResumeContentById(32L);

        List<ResumeContentDTO> out = resumeService.getResumeContentList();

        assertEquals(2, out.size());
        assertEquals(31L, out.get(0).getId());
        assertEquals(32L, out.get(1).getId());
        verify(resumeMapper).selectByUserId(1L);
        verify(resumeService).getResumeContentById(31L);
        verify(resumeService).getResumeContentById(32L);
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
                eq(ResumeImportParseResultDTO.class), eq(ModelEnum.DEEPSEEK_FLASH));

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.parseImport(request));

        assertEquals(ErrorCode.RESUME_IMPORT_PARSE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void parseImport_success_shouldNormalizeAndUseDeepSeekFlash() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume body");

        ResumeImportParseResultDTO llmResult = new ResumeImportParseResultDTO();
        ResumeImportParseResultDTO.EducationItem edu = new ResumeImportParseResultDTO.EducationItem();
        edu.setSchool("SCU");
        edu.setMajor("SE");
        edu.setDegree(99);
        edu.setStartDate("2020-9");
        edu.setEndDate("至今");
        llmResult.setEducations(List.of(edu));

        ResumeImportParseResultDTO.CareerItem blankCareer = new ResumeImportParseResultDTO.CareerItem();
        llmResult.setCareers(List.of(blankCareer));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResultDTO.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResultDTO out = resumeService.parseImport(request);

        assertEquals(1, out.getEducations().size());
        assertEquals("SCU", out.getEducations().get(0).getSchool());
        assertEquals(6, out.getEducations().get(0).getDegree());
        assertEquals("2020-09-01", out.getEducations().get(0).getStartDate());
        assertEquals(null, out.getEducations().get(0).getEndDate());
        assertEquals(0, out.getCareers().size());
        verify(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResultDTO.class), eq(ModelEnum.DEEPSEEK_FLASH));
    }

    @Test
    void parseImport_yearOnlyDate_shouldNormalizeToNull() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResultDTO llmResult = new ResumeImportParseResultDTO();
        ResumeImportParseResultDTO.EducationItem edu = new ResumeImportParseResultDTO.EducationItem();
        edu.setSchool("SCU");
        edu.setStartDate("2020");
        edu.setEndDate("2022年");
        llmResult.setEducations(List.of(edu));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResultDTO.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResultDTO out = resumeService.parseImport(request);

        assertEquals(null, out.getEducations().get(0).getStartDate());
        assertEquals(null, out.getEducations().get(0).getEndDate());
    }

    @Test
    void parseImport_chineseYearMonth_shouldUseFirstDay() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("resume");

        ResumeImportParseResultDTO llmResult = new ResumeImportParseResultDTO();
        ResumeImportParseResultDTO.CareerItem career = new ResumeImportParseResultDTO.CareerItem();
        career.setCompany("ACME");
        career.setStartDate("2021年3月");
        llmResult.setCareers(List.of(career));

        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResultDTO.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResultDTO out = resumeService.parseImport(request);

        assertEquals("2021-03-01", out.getCareers().get(0).getStartDate());
    }

    @Test
    void parseImport_longText_shouldTruncateAndAddWarning() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeImportParseRequest request = new ResumeImportParseRequest();
        request.setRawText("a".repeat(16000));

        ResumeImportParseResultDTO llmResult = new ResumeImportParseResultDTO();
        doReturn(llmResult).when(chatUtil).chatStructuredOnceWithPlatformModel(
                any(String.class), eq(PromptNames.RESUME_IMPORT_PARSE), isNull(),
                eq(ResumeImportParseResultDTO.class), eq(ModelEnum.DEEPSEEK_FLASH));

        ResumeImportParseResultDTO out = resumeService.parseImport(request);

        assertNotNull(out.getWarnings());
        assertEquals(true, out.getWarnings().stream().anyMatch(w -> w.contains("截断")));
    }
}

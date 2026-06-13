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
import com.zdmj.resumeService.entity.Resume;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.mapper.EducationMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.mapper.ResumeMapper;
import com.zdmj.resumeService.mapper.SkillMapper;
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
    @Mock
    private EducationService educationService;
    @Mock
    private CareerService careerService;
    @Mock
    private ProjectExperienceService projectExperienceService;
    @Mock
    private SkillService skillService;
    @Mock
    private Validator validator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ResumeServiceImpl resumeService;

    @BeforeEach
    void setUp() {
        resumeService = spy(new ResumeServiceImpl(
                educationMapper, projectExperienceMapper, careerMapper, skillMapper, chatUtil, objectMapper,
                educationService, careerService, projectExperienceService, skillService, validator));
        ReflectionTestUtils.setField(Objects.requireNonNull(resumeService), "baseMapper", resumeMapper);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void create_userAlreadyHasResume_shouldThrow() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setName("resume");
        dto.setSkillId(99L);
        doReturn(true).when(resumeMapper).existsByUserId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.create(dto));

        assertEquals(ErrorCode.RESUME_ALREADY_EXISTS.getCode(), ex.getCode());
        verify(resumeService, never()).save(any(Resume.class));
    }

    @Test
    void create_nameExists_shouldThrowAndSkipSave() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setName("same-name");
        dto.setSkillId(99L);
        doReturn(false).when(resumeMapper).existsByUserId(1L);
        doReturn(true).when(resumeMapper).existsByName(1L, "same-name", null);

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.create(dto));

        assertEquals(ErrorCode.RESUME_NAME_EXISTS.getCode(), ex.getCode());
        verify(resumeMapper).existsByName(1L, "same-name", null);
        verify(resumeService, never()).save(any(Resume.class));
    }

    @Test
    void create_success_shouldSaveResumeShell() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setName("java-backend");
        dto.setSkillId(99L);
        doReturn(false).when(resumeMapper).existsByUserId(1L);
        doReturn(false).when(resumeMapper).existsByName(1L, "java-backend", null);
        doReturn(true).when(resumeService).save(any(Resume.class));

        Resume out = resumeService.create(dto);

        assertEquals(1L, out.getUserId());
        assertEquals("java-backend", out.getName());
        assertEquals(99L, out.getSkillId());
        verify(resumeMapper).existsByUserId(1L);
        verify(resumeService).save(any(Resume.class));
    }

    @Test
    void create_saveFailed_shouldThrowCreateFailed() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ResumeDTO dto = new ResumeDTO();
        dto.setName("java-backend");
        dto.setSkillId(99L);
        doReturn(false).when(resumeMapper).existsByUserId(1L);
        doReturn(false).when(resumeMapper).existsByName(1L, "java-backend", null);
        doReturn(false).when(resumeService).save(any(Resume.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> resumeService.create(dto));

        assertEquals(ErrorCode.RESUME_CREATE_FAILED.getCode(), ex.getCode());
        verify(resumeService).save(any(Resume.class));
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
    void update_success_shouldPersistShellFields() {
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
        doReturn(true).when(resumeService).updateById(any(Resume.class));

        Resume out = resumeService.update(dto);

        assertEquals("new-name", out.getName());
        assertEquals(5L, out.getSkillId());
        verify(resumeMapper).existsByName(1L, "new-name", 10L);
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
        dto.setSkillId(5L);
        Resume existing = new Resume();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("old-name");
        doReturn(existing).when(resumeMapper).selectById(10L);
        doReturn(false).when(resumeMapper).existsByName(1L, "new-name", 10L);
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
    void getResumeContentList_shouldReturnSingleItemWhenResumeExists() {
        UserHolder.set(UserContext.of(1L, "u1"));
        Resume resume = new Resume();
        resume.setId(31L);
        resume.setUserId(1L);
        resume.setName("my-resume");
        doReturn(resume).when(resumeMapper).selectOneByUserId(1L);
        doReturn(List.of()).when(educationMapper).selectByUserId(1L);
        doReturn(List.of()).when(careerMapper).selectByUserId(1L);
        doReturn(List.of()).when(projectExperienceMapper).selectByUserId(1L);

        List<ResumeContentDTO> out = resumeService.getResumeContentList();

        assertEquals(1, out.size());
        assertEquals(31L, out.get(0).getId());
        verify(resumeMapper).selectOneByUserId(1L);
    }

    @Test
    void getResumeContentList_noResume_shouldReturnEmpty() {
        UserHolder.set(UserContext.of(1L, "u1"));
        doReturn(null).when(resumeMapper).selectOneByUserId(1L);

        List<ResumeContentDTO> out = resumeService.getResumeContentList();

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
        doReturn(true).when(resumeService).updateById(any(Resume.class));

        var request = new com.zdmj.resumeService.dto.ResumeContentSaveRequest();
        var skill = new com.zdmj.resumeService.dto.SkillDTO();
        skill.setId(9L);
        skill.setName("skills");
        request.setSkill(skill);
        request.setEducations(List.of());
        request.setCareers(List.of());
        request.setProjects(List.of());

        ResumeContentDTO out = resumeService.saveMyResumeContent(request);

        assertEquals(1L, out.getId());
        verify(skillService).update(any());
        verify(resumeService).updateById(resume);
    }
}

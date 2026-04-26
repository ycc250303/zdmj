package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.common.util.PromptUtil;
import com.zdmj.common.util.PromptUtil.JobRole;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateReqDTO;
import com.zdmj.resumeService.dto.ResumeRoleDetectDTO;
import com.zdmj.resumeService.dto.StudentCapabilityProfileDTO;
import com.zdmj.resumeService.entity.StudentCapabilityProfile;
import com.zdmj.resumeService.mapper.StudentCapabilityProfileMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StudentCapabilityProfileServiceImplTest {

    @Mock
    private ChatUtil chatUtil;
    @Mock
    private StudentCapabilityProfileMapper studentCapabilityProfileMapper;

    private StudentCapabilityProfileServiceImpl service;
    private static boolean tableInfoInitialized;

    @BeforeEach
    void setUp() {
        initMybatisPlusLambdaCache();
        service = spy(new StudentCapabilityProfileServiceImpl(chatUtil, new ObjectMapper()));
        ReflectionTestUtils.setField(service, "baseMapper", studentCapabilityProfileMapper);
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void getCurrentUserProfile_notFound_shouldThrow404() {
        doReturn(null).when(service).getOne(any());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getCurrentUserProfile());

        assertEquals(404, ex.getCode());
        verify(service).getOne(any());
    }

    @Test
    void getCurrentUserProfile_found_shouldReturnDto() {
        StudentCapabilityProfile profile = new StudentCapabilityProfile();
        profile.setUserId(1L);
        profile.setPromptName(PromptUtil.PromptNames.RESUME_ANALYSIS_JAVA_BACKEND);
        profile.setCompletenessScore(85);
        profile.setCompetitivenessScore(74);
        doReturn(profile).when(service).getOne(any());

        StudentCapabilityProfileDTO out = service.getCurrentUserProfile();

        assertNotNull(out);
        assertEquals(85, out.getCompletenessScore());
        assertEquals(74, out.getCompetitivenessScore());
    }

    @Test
    void generateProfile_missingInput_shouldThrow400AndSkipLlm() {
        CapabilityProfileGenerateReqDTO req = new CapabilityProfileGenerateReqDTO();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(400, ex.getCode());
        verify(chatUtil, never()).chatStructuredOnce(anyString(), anyString(), any(), eq(StudentCapabilityProfileDTO.class));
    }

    @Test
    void generateProfile_llmIllegalState_shouldThrow500() {
        CapabilityProfileGenerateReqDTO req = new CapabilityProfileGenerateReqDTO();
        req.setRawText("java spring boot redis mysql");
        doThrow(new IllegalStateException("bad schema")).when(chatUtil)
                .chatStructuredOnce(anyString(), anyString(), any(), eq(StudentCapabilityProfileDTO.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(500, ex.getCode());
        assertEquals("能力画像生成失败，请稍后重试", ex.getMessage());
        verify(chatUtil).chatStructuredOnce(anyString(), anyString(), any(), eq(StudentCapabilityProfileDTO.class));
    }

    @Test
    void generateProfile_llmRuntime_shouldThrow500() {
        CapabilityProfileGenerateReqDTO req = new CapabilityProfileGenerateReqDTO();
        req.setRawText("java spring boot redis mysql");
        doThrow(new RuntimeException("timeout")).when(chatUtil)
                .chatStructuredOnce(anyString(), anyString(), any(), eq(StudentCapabilityProfileDTO.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(500, ex.getCode());
        assertEquals("大模型生成能力画像失败，请稍后重试", ex.getMessage());
        verify(chatUtil).chatStructuredOnce(anyString(), anyString(), any(), eq(StudentCapabilityProfileDTO.class));
    }

    @Test
    void generateProfile_newProfile_shouldNormalizeAndSave() {
        CapabilityProfileGenerateReqDTO req = new CapabilityProfileGenerateReqDTO();
        req.setRawText("java spring boot redis mysql");

        StudentCapabilityProfileDTO ai = new StudentCapabilityProfileDTO();
        StudentCapabilityProfileDTO.ScoreDetail detail = new StudentCapabilityProfileDTO.ScoreDetail();
        detail.setContentCompletenessScore(7);
        ai.setScoreDetail(detail);
        ai.setCompetitivenessScore(null);
        ai.setStrengths(List.of("技术栈匹配"));
        ai.setSummary("可投递初级 Java 岗位");
        ai.setMissingSkills(List.of("性能调优"));
        ai.setWeakEvidenceItems(List.of("实习证据不足"));
        StudentCapabilityProfileDTO.Suggestion suggestion = new StudentCapabilityProfileDTO.Suggestion();
        suggestion.setCategory("项目");
        suggestion.setRecommendation("补充压测数据");
        ai.setSuggestions(List.of(suggestion));
        doReturn(ai).when(chatUtil).chatStructuredOnce(anyString(), anyString(), any(), eq(StudentCapabilityProfileDTO.class));
        doReturn(null).when(service).getOne(any());
        doReturn(true).when(service).save(any(StudentCapabilityProfile.class));

        StudentCapabilityProfileDTO out = service.generateProfile(req);

        assertNotNull(out);
        assertEquals(70, out.getCompletenessScore());
        assertEquals(0, out.getCompetitivenessScore());
        assertEquals("可投递初级 Java 岗位", out.getSummary());
        assertEquals(1, out.getStrengths().size());
        verify(service).save(any(StudentCapabilityProfile.class));
        verify(service, never()).updateById(any(StudentCapabilityProfile.class));
    }

    @Test
    void generateProfile_existingProfile_shouldUpdateById() {
        CapabilityProfileGenerateReqDTO req = new CapabilityProfileGenerateReqDTO();
        req.setRawText("java spring boot redis mysql");

        StudentCapabilityProfileDTO ai = new StudentCapabilityProfileDTO();
        ai.setCompetitivenessScore(66);
        doReturn(ai).when(chatUtil).chatStructuredOnce(anyString(), anyString(), any(), eq(StudentCapabilityProfileDTO.class));

        StudentCapabilityProfile existing = new StudentCapabilityProfile();
        existing.setId(88L);
        existing.setUserId(1L);
        doReturn(existing).when(service).getOne(any());
        doReturn(true).when(service).updateById(any(StudentCapabilityProfile.class));

        StudentCapabilityProfileDTO out = service.generateProfile(req);

        assertNotNull(out);
        assertEquals(66, out.getCompetitivenessScore());
        verify(service).updateById(any(StudentCapabilityProfile.class));
        verify(service, never()).save(any(StudentCapabilityProfile.class));
    }

    @Test
    void detect_keywordDirectHit_shouldReturnRuleAndSkipLlm() {
        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect",
                "Java Spring Boot MySQL Redis project");

        assertNotNull(out);
        assertEquals(JobRole.JAVA, out.getRole());
        assertNotNull(out.getReason());
        verify(chatUtil, never()).chatStructuredOnce(anyString(), eq(PromptUtil.PromptNames.JOB_DETECT),
                any(), any());
    }

    @Test
    void detect_llmUnknown_shouldFallbackToKeywordWeakHit() throws Exception {
        String resumeText = "java spring 实习经历";
        Object llmResult = buildRoleDetectLlmResult("unknown", 0.93, "不确定");
        doReturn(llmResult).when(chatUtil).chatStructuredOnce(eq(resumeText), eq(PromptUtil.PromptNames.JOB_DETECT),
                isNull(), any());

        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect", resumeText);

        assertNotNull(out);
        assertEquals(JobRole.JAVA, out.getRole());
        assertEquals(0.45, out.getConfidence());
        verify(chatUtil).chatStructuredOnce(eq(resumeText), eq(PromptUtil.PromptNames.JOB_DETECT), isNull(), any());
    }

    @Test
    void detect_llmThrows_shouldFallbackToKeywordWeakHit() {
        String resumeText = "java spring 项目";
        doThrow(new RuntimeException("llm timeout")).when(chatUtil)
                .chatStructuredOnce(eq(resumeText), eq(PromptUtil.PromptNames.JOB_DETECT), isNull(), any());

        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect", resumeText);

        assertNotNull(out);
        assertEquals(JobRole.JAVA, out.getRole());
        assertEquals(0.35, out.getConfidence());
        verify(chatUtil).chatStructuredOnce(eq(resumeText), eq(PromptUtil.PromptNames.JOB_DETECT), isNull(), any());
    }

    @Test
    void detect_emptyText_shouldReturnUnknown() {
        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect", "  ");

        assertNotNull(out);
        assertEquals(JobRole.UNKNOWN, out.getRole());
        assertEquals(0.0, out.getConfidence());
        verify(chatUtil, never()).chatStructuredOnce(anyString(), anyString(), any(), any());
    }

    @Test
    void detect_llmThrowsAndNoKeyword_shouldReturnUnknownFallback() {
        String resumeText = "golang rust";
        doThrow(new RuntimeException("llm timeout")).when(chatUtil)
                .chatStructuredOnce(eq(resumeText), eq(PromptUtil.PromptNames.JOB_DETECT), isNull(), any());

        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect", resumeText);

        assertNotNull(out);
        assertEquals(JobRole.UNKNOWN, out.getRole());
        assertEquals(0.2, out.getConfidence());
    }

    @Test
    void getCurrentUserProfileOrNull_notFound_shouldReturnNull() {
        doReturn(null).when(service).getOne(any());

        StudentCapabilityProfileDTO out = service.getCurrentUserProfileOrNull();

        assertNull(out);
        verify(service).getOne(any());
    }

    @Test
    void getCurrentUserProfileOrNull_profileExistsAndJsonValid_shouldHydrateNestedFields() {
        StudentCapabilityProfile profile = new StudentCapabilityProfile();
        profile.setUserId(1L);
        profile.setPromptName(PromptUtil.PromptNames.RESUME_ANALYSIS_JAVA_BACKEND);
        profile.setCompletenessScore(81);
        profile.setCompetitivenessScore(72);
        profile.setScoreDetail("{\"contentCompletenessScore\":8}");
        profile.setMissingSkills("[\"分布式\"]");
        profile.setWeakEvidenceItems("[\"量化指标不足\"]");
        profile.setSuggestions("[{\"category\":\"项目\",\"recommendation\":\"补充结果\"}]");
        doReturn(profile).when(service).getOne(any());

        StudentCapabilityProfileDTO out = service.getCurrentUserProfileOrNull();

        assertNotNull(out);
        assertEquals(81, out.getCompletenessScore());
        assertNotNull(out.getScoreDetail());
        assertEquals(8, out.getScoreDetail().getContentCompletenessScore());
        assertEquals(1, out.getMissingSkills().size());
        assertEquals("项目", out.getSuggestions().get(0).getCategory());
        verify(service).getOne(any());
    }

    @Test
    void getCurrentUserProfileOrNull_jsonInvalid_shouldNotThrowAndKeepBaseFields() {
        StudentCapabilityProfile profile = new StudentCapabilityProfile();
        profile.setUserId(1L);
        profile.setPromptName(PromptUtil.PromptNames.RESUME_ANALYSIS_FRONTEND);
        profile.setCompletenessScore(59);
        profile.setCompetitivenessScore(61);
        profile.setScoreDetail("{bad-json");
        profile.setMissingSkills("[\"react\"]");
        doReturn(profile).when(service).getOne(any());

        StudentCapabilityProfileDTO out = service.getCurrentUserProfileOrNull();

        assertNotNull(out);
        assertEquals(59, out.getCompletenessScore());
        assertEquals(61, out.getCompetitivenessScore());
        assertNull(out.getScoreDetail());
        verify(service).getOne(any());
    }

    @Test
    void generateProfile_completenessAlreadyPresent_shouldKeepAndUpdatePath() {
        CapabilityProfileGenerateReqDTO req = new CapabilityProfileGenerateReqDTO();
        req.setRawText("java spring 简历内容");

        StudentCapabilityProfileDTO ai = new StudentCapabilityProfileDTO();
        ai.setCompletenessScore(88);
        ai.setCompetitivenessScore(77);
        StudentCapabilityProfileDTO.ScoreDetail detail = new StudentCapabilityProfileDTO.ScoreDetail();
        detail.setContentCompletenessScore(3);
        ai.setScoreDetail(detail);
        doReturn(ai).when(chatUtil).chatStructuredOnce(anyString(), anyString(), any(), eq(StudentCapabilityProfileDTO.class));

        StudentCapabilityProfile existing = new StudentCapabilityProfile();
        existing.setId(123L);
        existing.setUserId(1L);
        doReturn(existing).when(service).getOne(any());
        doReturn(true).when(service).updateById(any(StudentCapabilityProfile.class));

        StudentCapabilityProfileDTO out = service.generateProfile(req);

        assertNotNull(out);
        assertEquals(88, out.getCompletenessScore());
        assertEquals(77, out.getCompetitivenessScore());
        verify(service).updateById(any(StudentCapabilityProfile.class));
        verify(service, never()).save(any(StudentCapabilityProfile.class));
    }

    @Test
    void normalizeProfileScores_competitivenessMissing_shouldFallbackToZero() {
        StudentCapabilityProfileDTO dto = new StudentCapabilityProfileDTO();
        dto.setCompletenessScore(66);
        dto.setCompetitivenessScore(null);

        ReflectionTestUtils.invokeMethod(service, "normalizeProfileScores", dto);

        assertEquals(66, dto.getCompletenessScore());
        assertEquals(0, dto.getCompetitivenessScore());
    }

    @Test
    void toJson_whenSerializationFailed_shouldReturnNull() {
        Object invalid = new Object() {
            @SuppressWarnings("unused")
            public Object self() {
                return this;
            }
        };

        String json = ReflectionTestUtils.invokeMethod(service, "toJson", invalid);

        assertNull(json);
    }

    @Test
    void generateProfile_userNotLogin_shouldThrowAndSkipLlm() {
        UserHolder.clear();
        CapabilityProfileGenerateReqDTO req = new CapabilityProfileGenerateReqDTO();
        req.setRawText("java spring");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(chatUtil, never()).chatStructuredOnce(anyString(), anyString(), any(), any());
    }

    @Test
    void generateProfile_pdfParseFailed_shouldThrow400() {
        CapabilityProfileGenerateReqDTO req = new CapabilityProfileGenerateReqDTO();
        req.setPdfUrl("https://invalid.example.com/resume.pdf");

        try (MockedStatic<PdfParserUtil> mocked = mockStatic(PdfParserUtil.class)) {
            mocked.when(() -> PdfParserUtil.extractTextFromUrl("https://invalid.example.com/resume.pdf"))
                    .thenThrow(new RuntimeException("pdf parse failed"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

            assertEquals(400, ex.getCode());
            verify(chatUtil, never()).chatStructuredOnce(anyString(), anyString(), any(), any());
        }
    }

    @Test
    void getCurrentUserProfileOrNull_userNotLogin_shouldThrow() {
        UserHolder.clear();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getCurrentUserProfileOrNull());

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(service, never()).getOne(any());
    }

    private static Object buildRoleDetectLlmResult(String roleCode, double confidence, String reason) throws Exception {
        Class<?> clazz = Class.forName("com.zdmj.resumeService.service.impl.StudentCapabilityProfileServiceImpl$RoleDetectLLMResult");
        Object result = clazz.getDeclaredConstructor().newInstance();
        ReflectionTestUtils.setField(result, "roleCode", roleCode);
        ReflectionTestUtils.setField(result, "confidence", confidence);
        ReflectionTestUtils.setField(result, "reason", reason);
        return result;
    }

    private static void initMybatisPlusLambdaCache() {
        if (tableInfoInitialized) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, StudentCapabilityProfile.class);
        tableInfoInitialized = true;
    }
}

package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.common.ai.JobRole;
import com.zdmj.common.ai.JobRoleDetector;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.common.ai.prompt.PromptNames;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.dto.ResumeRoleDetectDTO;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.entity.StudentCapabilityProfile;
import com.zdmj.resumeService.mapper.StudentCapabilityProfileMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StudentCapabilityProfileServiceImplTest {

    @Mock
    private ChatUtil chatUtil;
    @Mock
    private FileUploadService fileUploadService;
    @Mock
    private PdfParserUtil pdfParserUtil;
    @Mock
    private StudentCapabilityProfileMapper studentCapabilityProfileMapper;

    private StudentCapabilityProfileServiceImpl service;
    private static boolean tableInfoInitialized;

    @BeforeEach
    void setUp() {
        initMybatisPlusLambdaCache();
        service = spy(new StudentCapabilityProfileServiceImpl(chatUtil, new ObjectMapper(), fileUploadService,
                pdfParserUtil, new PromptUtil(new DefaultResourceLoader())));
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

        assertEquals(ErrorCode.CAPABILITY_PROFILE_NOT_FOUND.getCode(), ex.getCode());
        verify(service).getOne(any());
    }

    @Test
    void getCurrentUserProfile_found_shouldReturnDto() {
        StudentCapabilityProfile profile = new StudentCapabilityProfile();
        profile.setUserId(1L);
        profile.setPromptName("resume-analysis/java-backend");
        profile.setCompetitivenessScore(74);
        doReturn(profile).when(service).getOne(any());

        StudentCapabilityProfileResponse out = service.getCurrentUserProfile();

        assertNotNull(out);
        assertEquals(74, out.getCompetitivenessScore());
    }

    @Test
    void generateProfile_missingInput_shouldThrow400AndSkipLlm() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(chatUtil, never()).chatStructuredOnce(anyLong(), anyString(), anyString(), any(), eq(StudentCapabilityProfileResponse.class));
    }

    @Test
    void generateProfile_llmIllegalState_shouldThrow500() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setRawText("java spring boot redis mysql");
        doThrow(new IllegalStateException("bad schema")).when(chatUtil)
                .chatStructuredOnce(anyLong(), anyString(), anyString(), any(), eq(StudentCapabilityProfileResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(ErrorCode.CAPABILITY_PROFILE_GENERATION_FAILED.getCode(), ex.getCode());
        assertEquals("能力画像生成失败，请稍后重试", ex.getMessage());
        verify(chatUtil).chatStructuredOnce(anyLong(), anyString(), anyString(), any(), eq(StudentCapabilityProfileResponse.class));
    }

    @Test
    void generateProfile_llmRuntime_shouldThrow500() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setRawText("java spring boot redis mysql");
        doThrow(new RuntimeException("timeout")).when(chatUtil)
                .chatStructuredOnce(anyLong(), anyString(), anyString(), any(), eq(StudentCapabilityProfileResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(ErrorCode.CAPABILITY_PROFILE_GENERATION_FAILED.getCode(), ex.getCode());
        assertEquals("大模型生成能力画像失败，请稍后重试", ex.getMessage());
        verify(chatUtil).chatStructuredOnce(anyLong(), anyString(), anyString(), any(), eq(StudentCapabilityProfileResponse.class));
    }

    @Test
    void generateProfile_newProfile_shouldNormalizeAndSave() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setRawText("java spring boot redis mysql");

        StudentCapabilityProfileResponse ai = new StudentCapabilityProfileResponse();
        StudentCapabilityProfileResponse.ScoreDetail detail = new StudentCapabilityProfileResponse.ScoreDetail();
        detail.setContentCompletenessScore(7);
        ai.setScoreDetail(detail);
        ai.setStrengths(List.of("技术栈匹配"));
        ai.setSummary("可投递初级 Java 岗位");
        StudentCapabilityProfileResponse.Suggestion suggestion = new StudentCapabilityProfileResponse.Suggestion();
        suggestion.setCategory("技能缺失");
        suggestion.setIssue("缺少 JVM 调优相关实践");
        suggestion.setRecommendation("补充压测数据");
        ai.setSuggestions(List.of(suggestion));
        doReturn(ai).when(chatUtil).chatStructuredOnce(anyLong(), anyString(), anyString(), any(), eq(StudentCapabilityProfileResponse.class));
        doReturn(null).when(service).getOne(any());
        doReturn(true).when(service).save(any(StudentCapabilityProfile.class));

        StudentCapabilityProfileResponse out = service.generateProfile(req);

        assertNotNull(out);
        assertEquals(7, out.getCompetitivenessScore());
        assertEquals("可投递初级 Java 岗位", out.getSummary());
        assertEquals(1, out.getStrengths().size());
        verify(service).save(any(StudentCapabilityProfile.class));
        verify(service, never()).updateById(any(StudentCapabilityProfile.class));
        verify(fileUploadService, never()).deleteOwnedByUrl(anyString(), anyString());
    }

    @Test
    void generateProfile_withPdfUrl_shouldDeleteCosFileAfterSuccess() {
        String pdfUrl = "https://bucket.cos.ap-shanghai.myqcloud.com/user-1/profile/resume-abc.pdf";
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setPdfUrl(pdfUrl);

        when(pdfParserUtil.extractTextFromUrl(pdfUrl))
                .thenReturn("java spring boot redis mysql project experience");

        StudentCapabilityProfileResponse ai = new StudentCapabilityProfileResponse();
        StudentCapabilityProfileResponse.ScoreDetail detail = new StudentCapabilityProfileResponse.ScoreDetail();
        detail.setProjectExperienceScore(20);
        ai.setScoreDetail(detail);
        doReturn(ai).when(chatUtil).chatStructuredOnce(anyLong(), anyString(), anyString(), any(), eq(StudentCapabilityProfileResponse.class));
        doReturn(null).when(service).getOne(any());
        doReturn(true).when(service).save(any(StudentCapabilityProfile.class));

        StudentCapabilityProfileResponse out = service.generateProfile(req);

        assertNotNull(out);
        verify(fileUploadService).deleteOwnedByUrl(pdfUrl, "profile");
    }

    @Test
    void generateProfile_withPdfUrl_whenDeleteFails_shouldStillReturnResult() {
        String pdfUrl = "https://bucket.cos.ap-shanghai.myqcloud.com/user-1/profile/resume-abc.pdf";
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setPdfUrl(pdfUrl);

        when(pdfParserUtil.extractTextFromUrl(pdfUrl))
                .thenReturn("java spring boot redis mysql project experience");

        StudentCapabilityProfileResponse ai = new StudentCapabilityProfileResponse();
        StudentCapabilityProfileResponse.ScoreDetail detail = new StudentCapabilityProfileResponse.ScoreDetail();
        detail.setSkillMatchScore(10);
        ai.setScoreDetail(detail);
        doReturn(ai).when(chatUtil).chatStructuredOnce(anyLong(), anyString(), anyString(), any(), eq(StudentCapabilityProfileResponse.class));
        doReturn(null).when(service).getOne(any());
        doReturn(true).when(service).save(any(StudentCapabilityProfile.class));
        doThrow(new RuntimeException("cos delete failed")).when(fileUploadService).deleteOwnedByUrl(pdfUrl, "profile");

        StudentCapabilityProfileResponse out = service.generateProfile(req);

        assertNotNull(out);
        assertEquals(10, out.getCompetitivenessScore());
        verify(fileUploadService).deleteOwnedByUrl(pdfUrl, "profile");
    }

    @Test
    void generateProfile_existingProfile_shouldUpdateById() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setRawText("java spring boot redis mysql");

        StudentCapabilityProfileResponse ai = new StudentCapabilityProfileResponse();
        StudentCapabilityProfileResponse.ScoreDetail detail = new StudentCapabilityProfileResponse.ScoreDetail();
        detail.setProjectExperienceScore(30);
        detail.setSkillMatchScore(15);
        detail.setContentCompletenessScore(10);
        detail.setStructureClarityScore(8);
        detail.setExpressionProfessionalismScore(3);
        ai.setScoreDetail(detail);
        ai.setCompetitivenessScore(66);
        doReturn(ai).when(chatUtil).chatStructuredOnce(anyLong(), anyString(), anyString(), any(), eq(StudentCapabilityProfileResponse.class));

        StudentCapabilityProfile existing = new StudentCapabilityProfile();
        existing.setId(88L);
        existing.setUserId(1L);
        doReturn(existing).when(service).getOne(any());
        doReturn(true).when(service).updateById(any(StudentCapabilityProfile.class));

        StudentCapabilityProfileResponse out = service.generateProfile(req);

        assertNotNull(out);
        assertEquals(66, out.getCompetitivenessScore());
        verify(service).updateById(any(StudentCapabilityProfile.class));
        verify(service, never()).save(any(StudentCapabilityProfile.class));
    }

    @Test
    void detect_keywordDirectHit_shouldReturnRuleAndSkipLlm() {
        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect", 1L,
                "Java Spring Boot MySQL Redis project");

        assertNotNull(out);
        assertEquals(JobRole.JAVA, out.getRole());
        assertNotNull(out.getReason());
        verify(chatUtil, never()).chatStructuredOnce(any(), anyString(), eq(PromptNames.JOB_DETECT),
                any(), any());
    }

    @Test
    void detect_llmUnknown_shouldFallbackToKeywordWeakHit() throws Exception {
        String resumeText = "java spring 实习经历";
        Object llmResult = buildRoleDetectLlmResult("unknown", 0.93, "不确定");
        doReturn(llmResult).when(chatUtil).chatStructuredOnce(eq(1L), eq(resumeText), eq(PromptNames.JOB_DETECT),
                isNull(), any());

        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect", 1L, resumeText);

        assertNotNull(out);
        assertEquals(JobRole.JAVA, out.getRole());
        assertEquals(0.45, out.getConfidence());
        verify(chatUtil).chatStructuredOnce(eq(1L), eq(resumeText), eq(PromptNames.JOB_DETECT), isNull(), any());
    }

    @Test
    void detect_llmThrows_shouldFallbackToKeywordWeakHit() {
        String resumeText = "java spring 项目";
        doThrow(new RuntimeException("llm timeout")).when(chatUtil)
                .chatStructuredOnce(eq(1L), eq(resumeText), eq(PromptNames.JOB_DETECT), isNull(), any());

        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect", 1L, resumeText);

        assertNotNull(out);
        assertEquals(JobRole.JAVA, out.getRole());
        assertEquals(0.35, out.getConfidence());
        verify(chatUtil).chatStructuredOnce(eq(1L), eq(resumeText), eq(PromptNames.JOB_DETECT), isNull(), any());
    }

    @Test
    void detect_emptyText_shouldReturnUnknown() {
        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect", 1L, "  ");

        assertNotNull(out);
        assertEquals(JobRole.UNKNOWN, out.getRole());
        assertEquals(0.0, out.getConfidence());
        verify(chatUtil, never()).chatStructuredOnce(any(), any(), any(), any(), any());
    }

    @Test
    void detect_llmThrowsAndNoKeyword_shouldReturnUnknownFallback() {
        String resumeText = "golang rust";
        doThrow(new RuntimeException("llm timeout")).when(chatUtil)
                .chatStructuredOnce(eq(1L), eq(resumeText), eq(PromptNames.JOB_DETECT), isNull(), any());

        ResumeRoleDetectDTO out = ReflectionTestUtils.invokeMethod(service, "detect", 1L, resumeText);

        assertNotNull(out);
        assertEquals(JobRole.UNKNOWN, out.getRole());
        assertEquals(0.2, out.getConfidence());
    }

    @Test
    void getCurrentUserProfileOrNull_notFound_shouldReturnNull() {
        doReturn(null).when(service).getOne(any());

        StudentCapabilityProfileResponse out = service.getCurrentUserProfileOrNull();

        assertNull(out);
        verify(service).getOne(any());
    }

    @Test
    void getCurrentUserProfileOrNull_profileExistsAndJsonValid_shouldHydrateNestedFields() {
        StudentCapabilityProfile profile = new StudentCapabilityProfile();
        profile.setUserId(1L);
        profile.setPromptName("resume-analysis/java-backend");
        profile.setCompetitivenessScore(72);
        profile.setScoreDetail("{\"contentCompletenessScore\":8}");
        profile.setSuggestions("[{\"category\":\"项目\",\"issue\":\"缺量化\",\"recommendation\":\"补充结果\"}]");
        doReturn(profile).when(service).getOne(any());

        StudentCapabilityProfileResponse out = service.getCurrentUserProfileOrNull();

        assertNotNull(out);
        assertNotNull(out.getScoreDetail());
        assertEquals(8, out.getScoreDetail().getContentCompletenessScore());
        assertEquals(1, out.getSuggestions().size());
        assertEquals("项目", out.getSuggestions().get(0).getCategory());
        verify(service).getOne(any());
    }

    @Test
    void getCurrentUserProfileOrNull_jsonInvalid_shouldNotThrowAndKeepBaseFields() {
        StudentCapabilityProfile profile = new StudentCapabilityProfile();
        profile.setUserId(1L);
        profile.setPromptName("resume-analysis/frontend");
        profile.setCompetitivenessScore(61);
        profile.setScoreDetail("{bad-json");
        doReturn(profile).when(service).getOne(any());

        StudentCapabilityProfileResponse out = service.getCurrentUserProfileOrNull();

        assertNotNull(out);
        assertEquals(61, out.getCompetitivenessScore());
        assertNull(out.getScoreDetail());
        verify(service).getOne(any());
    }

    @Test
    void normalizeProfileScores_scoreDetailOutOfRange_shouldThrow() {
        StudentCapabilityProfileResponse dto = new StudentCapabilityProfileResponse();
        StudentCapabilityProfileResponse.ScoreDetail detail = new StudentCapabilityProfileResponse.ScoreDetail();
        detail.setProjectExperienceScore(50);
        dto.setScoreDetail(detail);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "normalizeProfileScores", dto));

        assertEquals(ErrorCode.CAPABILITY_PROFILE_SCORE_INVALID.getCode(), ex.getCode());
        assertNotNull(ex.getMessage());
    }

    @Test
    void normalizeProfileScores_scoreDetailInRange_shouldRecomputeTotal() {
        StudentCapabilityProfileResponse dto = new StudentCapabilityProfileResponse();
        StudentCapabilityProfileResponse.ScoreDetail detail = new StudentCapabilityProfileResponse.ScoreDetail();
        detail.setProjectExperienceScore(40);
        detail.setSkillMatchScore(20);
        detail.setContentCompletenessScore(15);
        detail.setStructureClarityScore(15);
        detail.setExpressionProfessionalismScore(10);
        dto.setScoreDetail(detail);

        ReflectionTestUtils.invokeMethod(service, "normalizeProfileScores", dto);

        assertEquals(100, dto.getCompetitivenessScore());
    }

    @Test
    void normalizeProfileScores_ignoresLlmCompetitivenessWhenNoScoreDetail_shouldFallbackToZero() {
        StudentCapabilityProfileResponse dto = new StudentCapabilityProfileResponse();
        dto.setCompetitivenessScore(88);

        ReflectionTestUtils.invokeMethod(service, "normalizeProfileScores", dto);

        assertEquals(0, dto.getCompetitivenessScore());
    }

    @Test
    void normalizeProfileScores_competitivenessMissing_shouldFallbackToZero() {
        StudentCapabilityProfileResponse dto = new StudentCapabilityProfileResponse();
        dto.setCompetitivenessScore(null);

        ReflectionTestUtils.invokeMethod(service, "normalizeProfileScores", dto);

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
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setRawText("java spring");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(chatUtil, never()).chatStructuredOnce(any(), any(), any(), any(), any());
    }

    @Test
    void generateProfile_pdfParseFailed_shouldThrow400() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setPdfUrl("https://invalid.example.com/resume.pdf");

        when(pdfParserUtil.extractTextFromUrl("https://invalid.example.com/resume.pdf"))
                .thenThrow(new RuntimeException("pdf parse failed"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(chatUtil, never()).chatStructuredOnce(any(), any(), any(), any(), any());
        verify(fileUploadService, never()).deleteOwnedByUrl(anyString(), anyString());
    }

    @Test
    void generateProfile_pdfUrlRejected_shouldPropagateBusinessException() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setPdfUrl("https://evil.example/resume.pdf");
        when(pdfParserUtil.extractTextFromUrl("https://evil.example/resume.pdf"))
                .thenThrow(new BusinessException(ErrorCode.URL_FORMAT_ERROR, "仅支持本系统已上传的文件"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(req));

        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(chatUtil, never()).chatStructuredOnce(any(), any(), any(), any(), any());
    }

    @Test
    void getCurrentUserProfileOrNull_userNotLogin_shouldThrow() {
        UserHolder.clear();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getCurrentUserProfileOrNull());

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(service, never()).getOne(any());
    }

    private static JobRoleDetector.RoleDetectLLMResult buildRoleDetectLlmResult(
            String roleCode, double confidence, String reason) {
        JobRoleDetector.RoleDetectLLMResult result = new JobRoleDetector.RoleDetectLLMResult();
        result.setRoleCode(roleCode);
        result.setConfidence(confidence);
        result.setReason(reason);
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

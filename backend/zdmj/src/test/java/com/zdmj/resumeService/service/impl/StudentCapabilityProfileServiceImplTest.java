package com.zdmj.resumeService.service.impl;

import com.zdmj.resumeService.api.StudentCapabilitySuggestion;
import com.zdmj.resumeService.api.StudentCapabilityScoreDetail;
import com.zdmj.common.context.CurrentActor;
import java.io.IOException;
import java.io.InputStream;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.jobService.api.JobRole;
import com.zdmj.jobService.service.impl.JobRoleDetectionServiceImpl;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.jobService.api.JobRoleDetectionResult;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.entity.StudentCapabilityProfile;
import com.zdmj.resumeService.mapper.StudentCapabilityProfileMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StudentCapabilityProfileServiceImplTest {

    private final CurrentActor actor = new CurrentActor(1L);

    @Mock
    private ModelGateway modelGateway;
    @Mock
    private PdfParserUtil pdfParserUtil;
    @Mock
    private StudentCapabilityProfileMapper studentCapabilityProfileMapper;

    private StudentCapabilityProfileServiceImpl service;
    private static boolean tableInfoInitialized;

    @BeforeEach
    void setUp() {
        initMybatisPlusLambdaCache();
        service = spy(new StudentCapabilityProfileServiceImpl(studentCapabilityProfileMapper, modelGateway, new ObjectMapper(),
                pdfParserUtil, new JobRoleDetectionServiceImpl(modelGateway)));
    }

    @Test
    void 公开画像读取_保留全部JSON字段并隔离集合修改() throws Exception {
        var source = new com.zdmj.resumeService.dto.StudentCapabilityProfileResponse();
        source.setTargetRoleType("java-backend");
        source.setProfessionalSkills("Java");
        source.setHonorsAndAwards("竞赛获奖");
        source.setInnovationAbility("创新实践");
        source.setLearningAbility("自主学习");
        source.setPressureResistance("按期交付");
        source.setCommunicationAbility("协作沟通");
        source.setPracticalAbility("实习实践");
        source.setCompetitivenessScore(80);
        source.setScoreDetail(new com.zdmj.resumeService.api.StudentCapabilityScoreDetail(30, 20, 10, 10, 10));
        source.setStrengths(new java.util.ArrayList<>(java.util.Arrays.asList("Java", null)));
        source.setSuggestions(new java.util.ArrayList<>(java.util.Arrays.asList(
                new com.zdmj.resumeService.api.StudentCapabilitySuggestion("技能", "高", "缺少证据", "完善项目"), null)));
        source.setSummary("画像评价");
        doReturn(source).when(service).getCurrentUserProfileOrNull(actor);
        var snapshot = service.findCurrentProfile(actor);
        var json = new ObjectMapper();
        org.assertj.core.api.Assertions.assertThat(json.<com.fasterxml.jackson.databind.JsonNode>valueToTree(snapshot))
                .isEqualTo(json.<com.fasterxml.jackson.databind.JsonNode>valueToTree(source));
        source.getStrengths().clear();
        source.getSuggestions().clear();
        org.assertj.core.api.Assertions.assertThat(snapshot.strengths()).containsExactly("Java", null);
        org.assertj.core.api.Assertions.assertThat(snapshot.suggestions()).hasSize(2);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.strengths().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.suggestions().clear());
        doReturn(null).when(service).getCurrentUserProfileOrNull(actor);
        org.junit.jupiter.api.Assertions.assertNull(service.findCurrentProfile(actor));
    }

    @Test
    void getCurrentUserProfile_notFound_shouldThrow404() {
        doReturn(null).when(studentCapabilityProfileMapper).selectOne(any());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getCurrentUserProfile(actor));

        assertEquals(ErrorCode.CAPABILITY_PROFILE_NOT_FOUND.getCode(), ex.getCode());
        verify(studentCapabilityProfileMapper).selectOne(any());
    }

    @Test
    void getCurrentUserProfile_found_shouldReturnDto() {
        StudentCapabilityProfile profile = new StudentCapabilityProfile();
        profile.setUserId(1L);
        profile.setPromptName("resume-analysis/java-backend");
        profile.setCompetitivenessScore(74);
        doReturn(profile).when(studentCapabilityProfileMapper).selectOne(any());

        StudentCapabilityProfileResponse out = service.getCurrentUserProfile(actor);

        assertNotNull(out);
        assertEquals(74, out.getCompetitivenessScore());
    }

    @Test
    void generateProfile_missingInput_shouldThrow400AndSkipLlm() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(actor, req));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(modelGateway, never()).generateStructured(any(), any(ModelRequest.class), eq(StudentCapabilityProfileResponse.class));
    }

    @Test
    void generateProfile_llmIllegalState_shouldThrow500() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest("java spring boot redis mysql");

        doThrow(new IllegalStateException("bad schema")).when(modelGateway)
                .generateStructured(any(), any(ModelRequest.class), eq(StudentCapabilityProfileResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(actor, req));

        assertEquals(ErrorCode.CAPABILITY_PROFILE_GENERATION_FAILED.getCode(), ex.getCode());
        assertEquals("能力画像生成失败，请稍后重试", ex.getMessage());
        verify(modelGateway).generateStructured(any(), any(ModelRequest.class), eq(StudentCapabilityProfileResponse.class));
    }

    @Test
    void generateProfile_llmRuntime_shouldThrow500() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest("java spring boot redis mysql");

        doThrow(new RuntimeException("timeout")).when(modelGateway)
                .generateStructured(any(), any(ModelRequest.class), eq(StudentCapabilityProfileResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(actor, req));

        assertEquals(ErrorCode.CAPABILITY_PROFILE_GENERATION_FAILED.getCode(), ex.getCode());
        assertEquals("大模型生成能力画像失败，请稍后重试", ex.getMessage());
        verify(modelGateway).generateStructured(any(), any(ModelRequest.class), eq(StudentCapabilityProfileResponse.class));
    }

    @Test
    void generateProfile_newProfile_shouldNormalizeAndSave() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest("java spring boot redis mysql");

        StudentCapabilityProfileResponse ai = new StudentCapabilityProfileResponse();
        StudentCapabilityScoreDetail detail = new StudentCapabilityScoreDetail(
                null, null, 7, null, null);

        ai.setScoreDetail(detail);
        ai.setStrengths(List.of("技术栈匹配"));
        ai.setSummary("可投递初级 Java 岗位");
        StudentCapabilitySuggestion suggestion = new StudentCapabilitySuggestion(
                "技能缺失", null, "缺少 JVM 调优相关实践", "补充压测数据");

        ai.setSuggestions(List.of(suggestion));
        doReturn(ai).when(modelGateway).generateStructured(any(), any(ModelRequest.class), eq(StudentCapabilityProfileResponse.class));
        doReturn(null).when(studentCapabilityProfileMapper).selectOne(any());
        doReturn(1).when(studentCapabilityProfileMapper).insert(any(StudentCapabilityProfile.class));

        StudentCapabilityProfileResponse out = service.generateProfile(actor, req);

        assertNotNull(out);
        assertEquals(7, out.getCompetitivenessScore());
        assertEquals("可投递初级 Java 岗位", out.getSummary());
        assertEquals(1, out.getStrengths().size());
        verify(studentCapabilityProfileMapper).insert(any(StudentCapabilityProfile.class));
        verify(studentCapabilityProfileMapper, never()).updateById(any(StudentCapabilityProfile.class));
    }

    @Test
    void generateProfile_withFile_shouldExtractTextAndCloseStream() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        InputStream input = org.mockito.Mockito.mock(InputStream.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenReturn(input);
        when(pdfParserUtil.extractText(same(input)))
                .thenReturn("java spring boot redis mysql project experience");

        StudentCapabilityProfileResponse ai = new StudentCapabilityProfileResponse();
        StudentCapabilityScoreDetail detail = new StudentCapabilityScoreDetail(20,
                null, null, null, null);

        ai.setScoreDetail(detail);
        doReturn(ai).when(modelGateway).generateStructured(any(), any(ModelRequest.class), eq(StudentCapabilityProfileResponse.class));
        doReturn(null).when(studentCapabilityProfileMapper).selectOne(any());
        doReturn(1).when(studentCapabilityProfileMapper).insert(any(StudentCapabilityProfile.class));

        StudentCapabilityProfileResponse out = service.generateProfile(actor, file);

        assertNotNull(out);
        verify(input).close();
    }

    @Test
    void generateProfile_emptyFile_shouldReject() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(actor, file));

        assertEquals(ErrorCode.FILE_EMPTY.getCode(), ex.getCode());
        verify(modelGateway, never()).generateStructured(any(), any(), any());
    }

    @Test
    void generateProfile_existingProfile_shouldUpdateById() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest("java spring boot redis mysql");

        StudentCapabilityProfileResponse ai = new StudentCapabilityProfileResponse();
        StudentCapabilityScoreDetail detail = new StudentCapabilityScoreDetail(30,
                15, 10, 8, 3);

        ai.setScoreDetail(detail);
        ai.setCompetitivenessScore(66);
        doReturn(ai).when(modelGateway).generateStructured(any(), any(ModelRequest.class), eq(StudentCapabilityProfileResponse.class));

        StudentCapabilityProfile existing = new StudentCapabilityProfile();
        existing.setId(88L);
        existing.setUserId(1L);
        doReturn(existing).when(studentCapabilityProfileMapper).selectOne(any());
        doReturn(1).when(studentCapabilityProfileMapper).updateById(any(StudentCapabilityProfile.class));

        StudentCapabilityProfileResponse out = service.generateProfile(actor, req);

        assertNotNull(out);
        assertEquals(66, out.getCompetitivenessScore());
        verify(studentCapabilityProfileMapper).updateById(any(StudentCapabilityProfile.class));
        verify(studentCapabilityProfileMapper, never()).insert(any(StudentCapabilityProfile.class));
    }

    @Test
    void detect_emptyText_shouldRewriteReasonAndSkipLlm() {
        JobRoleDetectionResult out = ReflectionTestUtils.invokeMethod(service, "detect", actor, "  ");

        assertNotNull(out);
        assertEquals(JobRole.UNKNOWN, out.role());
        assertEquals("简历文本为空", out.reason());
        verify(modelGateway, never()).generateStructured(any(), any(), any());
    }

    @Test
    void getCurrentUserProfileOrNull_notFound_shouldReturnNull() {
        doReturn(null).when(studentCapabilityProfileMapper).selectOne(any());

        StudentCapabilityProfileResponse out = service.getCurrentUserProfileOrNull(actor);

        assertNull(out);
        verify(studentCapabilityProfileMapper).selectOne(any());
    }

    @Test
    void getCurrentUserProfileOrNull_profileExistsAndJsonValid_shouldHydrateNestedFields() {
        StudentCapabilityProfile profile = new StudentCapabilityProfile();
        profile.setUserId(1L);
        profile.setPromptName("resume-analysis/java-backend");
        profile.setCompetitivenessScore(72);
        profile.setScoreDetail("{\"contentCompletenessScore\":8}");
        profile.setSuggestions("[{\"category\":\"项目\",\"issue\":\"缺量化\",\"recommendation\":\"补充结果\"}]");
        doReturn(profile).when(studentCapabilityProfileMapper).selectOne(any());

        StudentCapabilityProfileResponse out = service.getCurrentUserProfileOrNull(actor);

        assertNotNull(out);
        assertNotNull(out.getScoreDetail());
        assertEquals(8, out.getScoreDetail().contentCompletenessScore());
        assertEquals(1, out.getSuggestions().size());
        assertEquals("项目", out.getSuggestions().get(0).category());
        verify(studentCapabilityProfileMapper).selectOne(any());
    }

    @Test
    void getCurrentUserProfileOrNull_jsonInvalid_shouldThrow() {
        StudentCapabilityProfile profile = new StudentCapabilityProfile();
        profile.setUserId(1L);
        profile.setPromptName("resume-analysis/frontend");
        profile.setCompetitivenessScore(61);
        profile.setScoreDetail("{bad-json");
        doReturn(profile).when(studentCapabilityProfileMapper).selectOne(any());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.getCurrentUserProfileOrNull(actor));

        assertInstanceOf(JsonProcessingException.class, ex.getCause());
        verify(studentCapabilityProfileMapper, never()).insert(any(StudentCapabilityProfile.class));
        verify(studentCapabilityProfileMapper, never()).updateById(any(StudentCapabilityProfile.class));
    }

    @Test
    void normalizeProfileScores_scoreDetailOutOfRange_shouldThrow() {
        StudentCapabilityProfileResponse dto = new StudentCapabilityProfileResponse();
        StudentCapabilityScoreDetail detail = new StudentCapabilityScoreDetail(50,
                null, null, null, null);

        dto.setScoreDetail(detail);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "normalizeProfileScores", dto));

        assertEquals(ErrorCode.CAPABILITY_PROFILE_SCORE_INVALID.getCode(), ex.getCode());
        assertNotNull(ex.getMessage());
    }

    @Test
    void normalizeProfileScores_scoreDetailInRange_shouldRecomputeTotal() {
        StudentCapabilityProfileResponse dto = new StudentCapabilityProfileResponse();
        StudentCapabilityScoreDetail detail = new StudentCapabilityScoreDetail(40,
                20, 15, 15, 10);

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
    void toJson_whenValueMissing_shouldReturnNull() {
        String json = ReflectionTestUtils.invokeMethod(service, "toJson", new Object[] {null});

        assertNull(json);
    }

    @Test
    void toJson_whenSerializationFailed_shouldThrowWithCause() {
        Object invalid = new Object() {
            @SuppressWarnings("unused")
            public Object self() {
                return this;
            }
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "toJson", invalid));

        assertInstanceOf(JsonProcessingException.class, ex.getCause());
    }

    @Test
    void generateProfile_whenScoreSerializationFails_shouldThrowAndSkipSave() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest("java spring boot redis mysql");

        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                if (value instanceof StudentCapabilityScoreDetail) {
                    throw new JsonWriteFailure("score");
                }
                return super.writeValueAsString(value);
            }
        });

        StudentCapabilityProfileResponse ai = new StudentCapabilityProfileResponse();
        StudentCapabilityScoreDetail detail = new StudentCapabilityScoreDetail(
                null, null, 7, null, null);

        ai.setScoreDetail(detail);
        doReturn(ai).when(modelGateway).generateStructured(any(), any(ModelRequest.class), eq(StudentCapabilityProfileResponse.class));
        doReturn(null).when(studentCapabilityProfileMapper).selectOne(any());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.generateProfile(actor, req));

        assertInstanceOf(JsonProcessingException.class, ex.getCause());
        verify(studentCapabilityProfileMapper, never()).insert(any(StudentCapabilityProfile.class));
        verify(studentCapabilityProfileMapper, never()).updateById(any(StudentCapabilityProfile.class));
    }

    @Test
    void generateProfile_pdfParseFailed_shouldThrow400() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        InputStream input = org.mockito.Mockito.mock(InputStream.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenReturn(input);
        when(pdfParserUtil.extractText(same(input)))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "文件文本提取失败"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(actor, file));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(input).close();
        verify(modelGateway, never()).generateStructured(any(), any(), any());
    }

    @Test
    void generateProfile_unreadableFile_shouldFail() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenThrow(new IOException("disk"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateProfile(actor, file));

        assertEquals(ErrorCode.FILE_READ_FAILED.getCode(), ex.getCode());
        verify(modelGateway, never()).generateStructured(any(), any(), any());
    }

    private static final class JsonWriteFailure extends JsonProcessingException {
        private JsonWriteFailure(String message) {
            super(message);
        }
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

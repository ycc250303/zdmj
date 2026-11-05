package com.zdmj.careerReportService.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.careerReportService.dto.CareerReportCheckResponse;
import com.zdmj.careerReportService.dto.CareerReportResponse;
import com.zdmj.careerReportService.dto.CareerReportGenerateRequest;
import com.zdmj.careerReportService.dto.CareerReportUpdateRequest;
import com.zdmj.careerReportService.entity.CareerDevelopmentReport;
import com.zdmj.careerReportService.mapper.CareerDevelopmentReportMapper;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.StructuredModelRequest;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.dto.JobCareerGraphResponse;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobCareerGraphService;
import com.zdmj.jobService.service.JobService;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.service.JobStudentMatchService;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.zdmj.aiService.api.EmbeddingGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CareerDevelopmentReportServiceImplTest {

    @Mock
    private ModelGateway modelGateway;
    @Mock
    private JobService jobService;
    @Mock
    private JobCapabilityProfileService jobCapabilityProfileService;
    @Mock
    private JobCareerGraphService jobCareerGraphService;
    @Mock
    private JobStudentMatchService jobStudentMatchService;
    @Mock
    private StudentCapabilityProfileService studentCapabilityProfileService;
    @Mock
    private KnowledgeBasesService knowledgeBasesService;
    @Mock
    private KnowledgeVectorMapper knowledgeVectorMapper;
    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;
    @Mock
    private KnowledgeEmbeddingService knowledgeEmbeddingService;
    @Mock
    private EmbeddingGateway embeddingGateway;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private CareerDevelopmentReportMapper reportMapper;

    private CareerDevelopmentReportServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CareerDevelopmentReport.class);
    }

    @BeforeEach
    void setUp() {
        UserHolder.set(UserContext.of(1L, "u1"));
        service = spy(new CareerDevelopmentReportServiceImpl(
                new ObjectMapper(),
                modelGateway,
                jobService,
                jobCapabilityProfileService,
                jobCareerGraphService,
                jobStudentMatchService,
                studentCapabilityProfileService,
                knowledgeBasesService,
                knowledgeVectorMapper,
                knowledgeDocumentMapper,
                knowledgeEmbeddingService,
                embeddingGateway,
                executingTemplate(),
                jdbcTemplate));
        ReflectionTestUtils.setField(service, "baseMapper", reportMapper);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void generate_shouldCreateV1Report_whenDependenciesReady() throws Exception {
        Long jobId = 10L;
        doReturn(buildJob()).when(jobService).getDetail(jobId);
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService).getCurrentUserProfileOrNull();
        doReturn(buildJobProfile()).when(jobCapabilityProfileService).getJobCapabilityProfileOrNull(jobId);
        doReturn(buildMatch()).when(jobStudentMatchService).getOrNull(jobId);
        doReturn(buildGraph()).when(jobCareerGraphService).getOrNull(jobId);
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.1f, 0.2f}));
        stubInsertNewVersion(999L);

        doAnswer(invocation -> {
            StructuredModelRequest<?> request = invocation.getArgument(1, StructuredModelRequest.class);
            Class<?> outputClass = request.outputType();
            Object payload = outputClass.getDeclaredConstructor().newInstance();
            Field f = outputClass.getDeclaredField("reportContent");
            f.setAccessible(true);
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("careerExploration", "匹配分析");
            content.put("careerGoals", List.of("目标1"));
            content.put("careerPath", List.of("路径A"));
            Map<String, Object> actionPlan = new LinkedHashMap<>();
            actionPlan.put("shortTerm", List.of(Map.of("task", "补齐技能", "cycle", "4周", "deliverable", "项目Demo")));
            actionPlan.put("midTerm", List.of(Map.of("task", "实习投递", "cycle", "8周", "deliverable", "投递清单")));
            content.put("actionPlan", actionPlan);
            content.put("evaluationPlan", Map.of("cycle", "双周", "metrics", List.of("完成率")));
            content.put("evidence", List.of("匹配短板A"));
            f.set(payload, content);
            return payload;
        }).when(modelGateway).generateStructured(any(), any());

        CareerReportResponse dto = service.generate(jobId, new CareerReportGenerateRequest());

        assertEquals(999L, dto.getId());
        assertThat(dto.getCompletenessScore()).isGreaterThanOrEqualTo(60);
        verify(reportMapper).insert(argThat(saved -> Long.valueOf(300L).equals(saved.getMatchId())
                && Long.valueOf(400L).equals(saved.getCareerGraphId())));
    }

    @Test
    void generate_whenEmbeddingFails_shouldThrowAndSkipSave() {
        Long jobId = 10L;
        doReturn(buildJob()).when(jobService).getDetail(jobId);
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService).getCurrentUserProfileOrNull();
        doReturn(buildJobProfile()).when(jobCapabilityProfileService).getJobCapabilityProfileOrNull(jobId);
        doReturn(buildMatch()).when(jobStudentMatchService).getOrNull(jobId);
        doReturn(buildGraph()).when(jobCareerGraphService).getOrNull(jobId);
        when(embeddingGateway.embed(any(), any())).thenThrow(new RuntimeException("embed fail"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generate(jobId, new CareerReportGenerateRequest()));

        assertEquals(ErrorCode.CAREER_REPORT_GENERATION_FAILED.getCode(), ex.getCode());
        assertEquals("embed fail", ex.getCause().getMessage());
        verify(modelGateway, never()).generateStructured(any(), any());
        verify(reportMapper, never()).insert(any(CareerDevelopmentReport.class));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void generate_whenReportContentSerializationFails_shouldThrowAndSkipSave() throws Exception {
        Long jobId = 10L;
        doReturn(buildJob()).when(jobService).getDetail(jobId);
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService).getCurrentUserProfileOrNull();
        doReturn(buildJobProfile()).when(jobCapabilityProfileService).getJobCapabilityProfileOrNull(jobId);
        doReturn(buildMatch()).when(jobStudentMatchService).getOrNull(jobId);
        doReturn(buildGraph()).when(jobCareerGraphService).getOrNull(jobId);
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.1f, 0.2f}));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                if (value instanceof Map<?, ?>) {
                    throw new JsonWriteFailure("report");
                }
                return super.writeValueAsString(value);
            }
        });
        doAnswer(invocation -> {
            StructuredModelRequest<?> request = invocation.getArgument(1, StructuredModelRequest.class);
            Class<?> outputClass = request.outputType();
            Object payload = outputClass.getDeclaredConstructor().newInstance();
            Field reportContent = outputClass.getDeclaredField("reportContent");
            reportContent.setAccessible(true);
            reportContent.set(payload, Map.of("careerExploration", "匹配分析"));
            return payload;
        }).when(modelGateway).generateStructured(any(), any());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.generate(jobId, new CareerReportGenerateRequest()));

        assertInstanceOf(JsonProcessingException.class, ex.getCause());
        verify(reportMapper, never()).insert(any(CareerDevelopmentReport.class));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void generate_shouldSearchUserAndLearningPathKnowledgeBases_whenEmbeddingReady() throws Exception {
        Long jobId = 10L;
        doReturn(buildJob()).when(jobService).getDetail(jobId);
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService).getCurrentUserProfileOrNull();
        doReturn(buildJobProfile()).when(jobCapabilityProfileService).getJobCapabilityProfileOrNull(jobId);
        doReturn(buildMatch()).when(jobStudentMatchService).getOrNull(jobId);
        doReturn(buildGraph()).when(jobCareerGraphService).getOrNull(jobId);
        doReturn(100L).when(knowledgeBasesService).getOrCreateKnowledgeBaseId();
        doReturn(9L).when(knowledgeBasesService).findKnowledgeBaseIdByScope(KnowledgeScopeEnum.LEARNING_PATH.getCode());

        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[] {0.1f, 0.2f}));
        when(knowledgeEmbeddingService.toPgVector(org.mockito.ArgumentMatchers.any())).thenReturn("[0.1,0.2]");

        KnowledgeRetrivalDTO learningHit = new KnowledgeRetrivalDTO();
        learningHit.setDocumentId(27L);
        learningHit.setChunkIndex(0);
        learningHit.setScore(0.82);
        learningHit.setDocCategory("learning_path");
        learningHit.setContent("Java 学习路线片段");
        doReturn(List.of()).when(knowledgeVectorMapper).searchBySimilarity(eq(1L), eq(100L), any(), anyInt());
        doReturn(List.of(learningHit)).when(knowledgeVectorMapper)
                .searchBySimilarity(eq(KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID), eq(9L), any(), anyInt());
        stubInsertNewVersion(1000L);

        doAnswer(invocation -> {
            StructuredModelRequest<?> request = invocation.getArgument(1, StructuredModelRequest.class);
            Class<?> outputClass = request.outputType();
            Object payload = outputClass.getDeclaredConstructor().newInstance();
            Field f = outputClass.getDeclaredField("reportContent");
            f.setAccessible(true);
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("careerExploration", "探索");
            content.put("careerGoals", List.of("目标1"));
            content.put("careerPath", List.of("路径A"));
            Map<String, Object> actionPlan = new LinkedHashMap<>();
            actionPlan.put("shortTerm", List.of(Map.of("task", "补齐技能", "cycle", "4周", "deliverable", "Demo")));
            actionPlan.put("midTerm", List.of(Map.of("task", "实习", "cycle", "8周", "deliverable", "清单")));
            content.put("actionPlan", actionPlan);
            content.put("evaluationPlan", Map.of("cycle", "双周", "metrics", List.of("完成率")));
            content.put("evidence", List.of("证据A"));
            f.set(payload, content);
            return payload;
        }).when(modelGateway).generateStructured(any(), any());

        service.generate(jobId, new CareerReportGenerateRequest());

        verify(knowledgeVectorMapper).searchBySimilarity(eq(1L), eq(100L), eq("[0.1,0.2]"), eq(8));
        verify(knowledgeVectorMapper).searchBySimilarity(
                eq(KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID), eq(9L), eq("[0.1,0.2]"), eq(8));
    }

    @Test
    void getLatestOrNull_shouldReplacePlaceholderKnowledgeSourceTitle() throws Exception {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(77L);
        doc.setTitle("测试开发学习路线");
        when(knowledgeDocumentMapper.selectBatchIds(List.of(77L))).thenReturn(List.of(doc));

        CareerDevelopmentReport entity = new CareerDevelopmentReport();
        entity.setId(50L);
        entity.setUserId(1L);
        entity.setJobId(10L);
        entity.setKnowledgeSources(new ObjectMapper().writeValueAsString(List.of(
                Map.of("documentId", 77, "title", "文档 #77", "score", 0.71, "metadata", Map.of()))));

        doReturn(entity).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());

        CareerReportResponse dto = service.getLatestOrNull(10L);

        assertThat(dto.getKnowledgeSources()).isNotEmpty();
        assertEquals("测试开发学习路线", dto.getKnowledgeSources().get(0).get("title"));
    }

    @Test
    void getLatestOrNull_whenStoredJsonBlank_shouldReturnEmptyContent() {
        CareerDevelopmentReport entity = new CareerDevelopmentReport();
        entity.setId(51L);
        entity.setUserId(1L);
        entity.setJobId(10L);
        doReturn(entity).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());

        CareerReportResponse dto = service.getLatestOrNull(10L);

        assertThat(dto.getReportContent()).isEmpty();
        assertThat(dto.getQualityFlags()).isEmpty();
        assertThat(dto.getKnowledgeSources()).isEmpty();
    }

    @Test
    void getLatestOrNull_whenReportContentInvalid_shouldThrowAndSkipUpdate() {
        CareerDevelopmentReport entity = new CareerDevelopmentReport();
        entity.setId(52L);
        entity.setUserId(1L);
        entity.setJobId(10L);
        entity.setReportContent("{bad-json");
        doReturn(entity).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.getLatestOrNull(10L));

        assertInstanceOf(JsonProcessingException.class, ex.getCause());
        verify(reportMapper, never()).insert(any(CareerDevelopmentReport.class));
        verify(reportMapper, never()).updateById(any(CareerDevelopmentReport.class));
    }

    @Test
    void checkIntegrity_shouldMarkFailed_whenSectionsMissing() {
        CareerDevelopmentReport existing = new CareerDevelopmentReport();
        existing.setId(20L);
        existing.setUserId(1L);
        existing.setJobId(10L);
        existing.setReportContent("{\"careerGoals\":[]}");
        doReturn(existing).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());
        when(reportMapper.selectOne(any())).thenReturn(existing);
        when(reportMapper.update(isNull(), any())).thenReturn(1);
        doThrow(new RuntimeException("llm failed"))
                .when(modelGateway).generateStructured(any(), argThat((StructuredModelRequest<?> req) -> req.promptVars() == null && req.outputType() == CareerReportCheckResponse.class));

        CareerReportCheckResponse check = service.checkIntegrity(20L);

        assertFalse(check.getPassed());
        assertThat(check.getMissingSections()).contains("职业探索");
        verify(reportMapper).update(isNull(), any());
        verify(reportMapper, never()).updateById(any(CareerDevelopmentReport.class));
        verify(jdbcTemplate).execute(any(ConnectionCallback.class));
    }

    @Test
    void saveManualEdit_shouldOverwriteSameReport() {
        CareerDevelopmentReport existing = new CareerDevelopmentReport();
        existing.setId(31L);
        existing.setUserId(1L);
        existing.setJobId(11L);
        existing.setReportContent("{\"careerExploration\":\"old\"}");
        existing.setPromptName("career-report/generate");
        doReturn(existing).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());
        when(reportMapper.selectOne(any())).thenReturn(existing);
        when(reportMapper.updateById(any(CareerDevelopmentReport.class))).thenAnswer(invocation -> {
            CareerDevelopmentReport saved = invocation.getArgument(0);
            assertEquals(31L, saved.getId());
            return 1;
        });

        CareerReportResponse first = service.saveManualEdit(31L, validUpdateRequest());
        CareerReportResponse second = service.saveManualEdit(31L, validUpdateRequest());

        assertEquals(31L, first.getId());
        assertEquals(31L, second.getId());
        verify(reportMapper, times(2)).updateById(any(CareerDevelopmentReport.class));
        verify(reportMapper, never()).insert(any(CareerDevelopmentReport.class));
    }

    @Test
    void saveManualEdit_emptyContent_shouldThrowValidationError() {
        CareerDevelopmentReport existing = new CareerDevelopmentReport();
        existing.setId(31L);
        existing.setUserId(1L);
        doReturn(existing).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());
        CareerReportUpdateRequest req = new CareerReportUpdateRequest();
        req.setReportContent(Map.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveManualEdit(31L, req));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(reportMapper, never()).insert(any(CareerDevelopmentReport.class));
    }

    @Test
    void saveManualEdit_whenOverwriteMisses_shouldNotReturnSuccess() {
        CareerDevelopmentReport existing = new CareerDevelopmentReport();
        existing.setId(31L);
        existing.setUserId(1L);
        existing.setJobId(11L);
        existing.setPromptName("career-report/generate");
        doReturn(existing).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());
        when(reportMapper.selectOne(any())).thenReturn(existing);
        when(reportMapper.updateById(any(CareerDevelopmentReport.class))).thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.saveManualEdit(31L, validUpdateRequest()));

        assertEquals("职业发展报告未写入", ex.getMessage());
        verify(reportMapper, never()).insert(any(CareerDevelopmentReport.class));
    }

    private void stubInsertNewVersion(long id) {
        when(reportMapper.insert(any(CareerDevelopmentReport.class))).thenAnswer(invocation -> {
            CareerDevelopmentReport entity = invocation.getArgument(0);
            entity.setId(id);
            return 1;
        });
    }

    private static CareerReportUpdateRequest validUpdateRequest() {
        CareerReportUpdateRequest req = new CareerReportUpdateRequest();
        req.setReportContent(Map.of(
                "careerExploration", "new",
                "careerGoals", List.of("g1"),
                "careerPath", List.of("p1"),
                "actionPlan", Map.of(
                        "shortTerm", List.of(Map.of("task", "t1", "cycle", "2周", "deliverable", "d1")),
                        "midTerm", List.of(Map.of("task", "t2", "cycle", "4周", "deliverable", "d2"))),
                "evaluationPlan", Map.of("cycle", "monthly"),
                "evidence", List.of("e1")));
        return req;
    }

    private static TransactionTemplate executingTemplate() {
        PlatformTransactionManager manager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        lenient().when(manager.getTransaction(any())).thenReturn(org.mockito.Mockito.mock(TransactionStatus.class));
        return new TransactionTemplate(manager);
    }

    private static final class JsonWriteFailure extends JsonProcessingException {
        private JsonWriteFailure(String message) {
            super(message);
        }
    }

    private static JobListItemResponse buildJob() {
        JobListItemResponse dto = new JobListItemResponse();
        dto.setId(10L);
        dto.setJobName("Java后端");
        dto.setDescription("负责后端开发");
        dto.setCompanyName("示例公司");
        dto.setKeywords(List.of("Java", "Spring"));
        return dto;
    }

    private static StudentCapabilityProfileResponse buildStudentProfile() {
        StudentCapabilityProfileResponse dto = new StudentCapabilityProfileResponse();
        dto.setProfessionalSkills("Java, Spring");
        dto.setLearningAbility("较强");
        return dto;
    }

    private static JobCapabilityProfileResponse buildJobProfile() {
        JobCapabilityProfileResponse dto = new JobCapabilityProfileResponse();
        dto.setProfessionalSkills("Java/Spring");
        dto.setTargetRoleType("java-backend");
        return dto;
    }

    private static JobStudentMatchResponse buildMatch() {
        JobStudentMatchResponse dto = new JobStudentMatchResponse();
        dto.setId(300L);
        dto.setOverallScore(78);
        return dto;
    }

    private static JobCareerGraphResponse buildGraph() {
        JobCareerGraphResponse dto = new JobCareerGraphResponse();
        dto.setId(400L);
        dto.setSummary("路径清晰");
        return dto;
    }
}

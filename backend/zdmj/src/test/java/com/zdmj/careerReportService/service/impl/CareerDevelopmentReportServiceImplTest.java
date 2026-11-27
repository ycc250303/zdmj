package com.zdmj.careerReportService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.knowledgeService.api.KnowledgeRagService;
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
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.service.JobService;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.service.JobStudentMatchService;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    private final CurrentActor actor = new CurrentActor(1L);

    @Mock
    private ModelGateway modelGateway;
    @Mock
    private JobService jobService;
    @Mock
    private JobStudentMatchService jobStudentMatchService;
    @Mock
    private StudentCapabilityProfileService studentCapabilityProfileService;
    @Mock
    private KnowledgeRagService knowledgeRagService;
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
        service = spy(new CareerDevelopmentReportServiceImpl(
                new ObjectMapper(),
                modelGateway,
                jobService,
                jobStudentMatchService,
                studentCapabilityProfileService,
                knowledgeRagService,
                executingTemplate(),
                jdbcTemplate));
        ReflectionTestUtils.setField(service, "baseMapper", reportMapper);
        lenient().when(knowledgeRagService.resolveReportReferences(eq(actor), any())).thenAnswer(i -> i.getArgument(1));
    }

    @Test
    void generate_shouldSaveReport_whenDependenciesReady() throws Exception {
        Long jobId = 10L;
        doReturn(buildJob()).when(jobService).getDetail(jobId);
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService).getCurrentUserProfileOrNull(actor);
        doReturn(buildMatch()).when(jobStudentMatchService).getOrNull(actor, jobId);
        stubInsertReport(999L);

        doAnswer(invocation -> {
            ModelRequest request = invocation.getArgument(1, ModelRequest.class);
            assertThat(request.message()).contains("## 岗位信息");
            assertThat(request.message()).contains("## 人岗匹配");
            assertThat(request.message()).doesNotContain("## 岗位画像");
            Class<?> outputClass = invocation.getArgument(2);
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
        }).when(modelGateway).generateStructured(any(), any(), any());

        CareerReportResponse dto = service.generate(actor, jobId, new CareerReportGenerateRequest(null, null));

        assertEquals(999L, dto.id());
        assertThat(dto.completenessScore()).isGreaterThanOrEqualTo(60);
        verify(reportMapper).insert(argThat(saved -> Long.valueOf(300L).equals(saved.getMatchId())));
    }

    @Test
    void generate_whenRetrievalFails_shouldThrowAndSkipSave() {
        Long jobId = 10L;
        doReturn(buildJob()).when(jobService).getDetail(jobId);
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService).getCurrentUserProfileOrNull(actor);
        doReturn(buildMatch()).when(jobStudentMatchService).getOrNull(actor, jobId);
        when(knowledgeRagService.retrieveLearningPaths(eq(actor), any())).thenThrow(
                new BusinessException(ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED, new RuntimeException("embed fail")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generate(actor, jobId, new CareerReportGenerateRequest(null, null)));

        assertEquals(ErrorCode.CAREER_REPORT_GENERATION_FAILED.getCode(), ex.getCode());
        assertThat(ex).hasRootCauseMessage("embed fail");
        verify(modelGateway, never()).generateStructured(any(), any(), any());
        verify(reportMapper, never()).insert(any(CareerDevelopmentReport.class));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void generate_whenReportContentSerializationFails_shouldThrowAndSkipSave() throws Exception {
        Long jobId = 10L;
        doReturn(buildJob()).when(jobService).getDetail(jobId);
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService).getCurrentUserProfileOrNull(actor);
        doReturn(buildMatch()).when(jobStudentMatchService).getOrNull(actor, jobId);
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
            Class<?> outputClass = invocation.getArgument(2);
            Object payload = outputClass.getDeclaredConstructor().newInstance();
            Field reportContent = outputClass.getDeclaredField("reportContent");
            reportContent.setAccessible(true);
            reportContent.set(payload, Map.of("careerExploration", "匹配分析"));
            return payload;
        }).when(modelGateway).generateStructured(any(), any(), any());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.generate(actor, jobId, new CareerReportGenerateRequest(null, null)));

        assertInstanceOf(JsonProcessingException.class, ex.getCause());
        verify(reportMapper, never()).insert(any(CareerDevelopmentReport.class));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void getLatestOrNull_whenStoredJsonBlank_shouldReturnEmptyContent() {
        CareerDevelopmentReport entity = new CareerDevelopmentReport();
        entity.setId(51L);
        entity.setUserId(1L);
        entity.setJobId(10L);
        doReturn(entity).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());

        CareerReportResponse dto = service.getLatestOrNull(actor, 10L);

        assertThat(dto.reportContent()).isEmpty();
        assertThat(dto.qualityFlags()).isEmpty();
        assertThat(dto.knowledgeSources()).isEmpty();
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

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.getLatestOrNull(actor, 10L));

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
        CareerReportCheckResponse modelCheck = new CareerReportCheckResponse(true, 80, null, List.of(), List.of(),
                List.of());

        doReturn(modelCheck).when(modelGateway).generateStructured(any(), any(), any());

        CareerReportCheckResponse check = service.checkIntegrity(actor, 20L);

        assertFalse(check.passed());
        assertThat(check.missingSections()).contains("职业探索");
        verify(reportMapper).update(isNull(), any());
        verify(reportMapper, never()).updateById(any(CareerDevelopmentReport.class));
        verify(jdbcTemplate).execute(any(ConnectionCallback.class));
    }

    @Test
    void checkIntegrity_whenModelFails_shouldPropagateAndSkipSave() {
        stubOwnedReport();
        RuntimeException failure = new IllegalStateException("模型检查失败");
        doThrow(failure).when(modelGateway).generateStructured(any(), any(), any());

        assertThat(assertThrows(RuntimeException.class, () -> service.checkIntegrity(actor, 31L))).isSameAs(failure);
        verify(reportMapper, never()).update(isNull(), any());
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void checkIntegrity_whenModelReturnsNull_shouldRejectAndSkipSave() {
        stubOwnedReport();

        BusinessException failure = assertThrows(BusinessException.class, () -> service.checkIntegrity(actor, 31L));

        assertEquals(ErrorCode.CAREER_REPORT_INVALID.getCode(), failure.getCode());
        verify(reportMapper, never()).update(isNull(), any());
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"nullPayload", "nullContent", "emptyContent"})
    void polish_whenModelContentMissing_shouldRejectAndSkipSave(String scenario) throws Exception {
        stubOwnedReport();
        doAnswer(invocation -> {
            if (scenario.equals("nullPayload")) {
                return null;
            }
            Class<?> outputClass = invocation.getArgument(2);
            Object payload = outputClass.getDeclaredConstructor().newInstance();
            if (scenario.equals("emptyContent")) {
                Field field = outputClass.getDeclaredField("reportContent");
                field.setAccessible(true);
                field.set(payload, Map.of());
            }
            return payload;
        }).when(modelGateway).generateStructured(any(), any(), any());

        BusinessException failure = assertThrows(BusinessException.class, () -> service.polish(actor, 31L, null));

        assertEquals(ErrorCode.CAREER_REPORT_INVALID.getCode(), failure.getCode());
        verify(reportMapper, never()).updateById(any(CareerDevelopmentReport.class));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    private void stubOwnedReport() {
        CareerDevelopmentReport existing = new CareerDevelopmentReport();
        existing.setId(31L);
        existing.setUserId(1L);
        existing.setJobId(11L);
        existing.setReportContent("{\"careerExploration\":\"原正文\"}");
        existing.setPromptName("career-report/generate");
        doReturn(existing).when(service).getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());
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

        CareerReportResponse first = service.saveManualEdit(actor, 31L, validUpdateRequest());
        CareerReportResponse second = service.saveManualEdit(actor, 31L, validUpdateRequest());

        assertEquals(31L, first.id());
        assertEquals(31L, second.id());
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
        CareerReportUpdateRequest req = new CareerReportUpdateRequest(Map.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveManualEdit(actor, 31L, req));

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
                () -> service.saveManualEdit(actor, 31L, validUpdateRequest()));

        assertEquals("职业发展报告未写入", ex.getMessage());
        verify(reportMapper, never()).insert(any(CareerDevelopmentReport.class));
    }

    private void stubInsertReport(long id) {
        when(reportMapper.insert(any(CareerDevelopmentReport.class))).thenAnswer(invocation -> {
            CareerDevelopmentReport entity = invocation.getArgument(0);
            entity.setId(id);
            return 1;
        });
    }

    private static CareerReportUpdateRequest validUpdateRequest() {
        CareerReportUpdateRequest req = new CareerReportUpdateRequest(
                Map.of("careerExploration", "new", "careerGoals", List.of("g1"), "careerPath", List.of("p1"), "actionPlan", Map.of("shortTerm", List.of(Map.of("task", "t1", "cycle", "2周", "deliverable", "d1")), "midTerm", List.of(Map.of("task", "t2", "cycle", "4周", "deliverable", "d2"))), "evaluationPlan", Map.of("cycle", "monthly"), "evidence", List.of("e1")));

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
        JobListItemResponse dto = new JobListItemResponse(10L, "Java后端", null, "示例公司", "负责后端开发", null, null, null,
                null, null, null, List.of("Java", "Spring"), null, null, null);

        return dto;
    }

    private static StudentCapabilityProfileResponse buildStudentProfile() {
        StudentCapabilityProfileResponse dto = new StudentCapabilityProfileResponse();
        dto.setProfessionalSkills("Java, Spring");
        dto.setLearningAbility("较强");
        return dto;
    }

    private static JobStudentMatchResponse buildMatch() {
        JobStudentMatchResponse dto = new JobStudentMatchResponse(300L, null, null, 78, null, null, null, null,
                null, null, null, null);

        return dto;
    }
    @Test
    void 完整报告_检查结果通过且保留质量标记契约() {
        CareerReportCheckResponse check = CareerDevelopmentReportServiceImpl.checkContentIntegrity(completeReport());

        assertThat(check.passed()).isTrue();
        assertThat(check.completenessScore()).isEqualTo(100);
        assertThat(CareerDevelopmentReportServiceImpl.qualityFlags(check)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "missingSections", List.of(), "nonActionableItems", List.of(),
                "weakEvidenceItems", List.of(), "riskLevel", "low"));
    }

    @ParameterizedTest
    @MethodSource("missingValues")
    void 必备章节为空_标记缺失并保留原有评分(Object emptyValue) {
        Map<String, Object> report = completeReport();
        report.put("careerGoals", emptyValue);

        CareerReportCheckResponse check = CareerDevelopmentReportServiceImpl.checkContentIntegrity(report);

        assertThat(check.passed()).isFalse();
        assertThat(check.missingSections()).containsExactly("职业目标");
        assertThat(check.completenessScore()).isEqualTo(88);
    }

    static Stream<Object> missingValues() {
        return Stream.of(null, "  ", List.of(), Map.of());
    }

    @Test
    void 行动项使用周期和交付物别名_列表与分组形式都能校验() {
        Map<String, Object> report = completeReport();
        report.put("actionPlan", Map.of(
                "shortTerm", List.of(
                        Map.of("period", "两周", "milestone", "作品"),
                        Map.of("timeWindow", "四周", "output", List.of("报告"))),
                "midTerm", Map.of("items", List.of("复盘"))));

        assertThat(CareerDevelopmentReportServiceImpl.checkContentIntegrity(report).nonActionableItems()).isEmpty();
        assertThat(CareerDevelopmentReportServiceImpl.checkContentIntegrity(report).completenessScore()).isEqualTo(100);
    }

    @Test
    void 行动项形状或字段无效_标记具体位置并拒绝通过() {
        Map<String, Object> report = completeReport();
        report.put("actionPlan", Map.of(
                "shortTerm", List.of("模糊建议", Map.of("cycle", 2, "deliverable", " ")),
                "midTerm", Map.of("tasks", List.of())));

        CareerReportCheckResponse check = CareerDevelopmentReportServiceImpl.checkContentIntegrity(report);

        assertThat(check.nonActionableItems()).containsExactly(
                "shortTerm#1", "shortTerm#2:missingCycle", "shortTerm#2:missingDeliverable", "midTerm:missingTasks");
        assertThat(check.passed()).isFalse();
        assertThat(check.completenessScore()).isEqualTo(68);
        assertThat(check.riskLevel()).isEqualTo("medium");
    }

    @Test
    void 空报告_返回完整缺失列表及低完整度() {
        CareerReportCheckResponse check = CareerDevelopmentReportServiceImpl.checkContentIntegrity(Map.of());

        assertThat(check.missingSections()).containsExactly("职业探索", "职业目标", "职业路径", "行动计划", "评估计划");
        assertThat(check.nonActionableItems()).containsExactly("actionPlan");
        assertThat(check.weakEvidenceItems()).containsExactly("evidence");
        assertThat(check.completenessScore()).isEqualTo(26);
        assertThat(check.riskLevel()).isEqualTo("high");
    }

    @ParameterizedTest
    @MethodSource("missingValues")
    void 行动阶段为空_记录阶段名称(Object emptyValue) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("shortTerm", emptyValue);
        plan.put("midTerm", List.of(Map.of("cycle", "四周", "output", "清单")));
        Map<String, Object> report = completeReport();
        report.put("actionPlan", plan);

        String issue = emptyValue instanceof Map<?, ?> ? "shortTerm:missingTasks" : "shortTerm";
        assertThat(CareerDevelopmentReportServiceImpl.checkContentIntegrity(report).nonActionableItems()).containsExactly(issue);
    }

    @Test
    void 合并检查_问题按出现顺序去重并保留任一未通过结论() {
        CareerReportCheckResponse local = CareerDevelopmentReportServiceImpl.checkContentIntegrity(Map.of());
        CareerReportCheckResponse model = new CareerReportCheckResponse(true, 80, null,
                Arrays.asList("职业探索", "引用说明", null), List.of("actionPlan", "验收标准"), null);

        CareerReportCheckResponse check = CareerDevelopmentReportServiceImpl.mergeIntegrityChecks(local, model);

        assertThat(check.passed()).isFalse();
        assertThat(check.completenessScore()).isEqualTo(53);
        assertThat(check.missingSections()).containsExactly("职业探索", "职业目标", "职业路径", "行动计划", "评估计划", "引用说明");
        assertThat(check.nonActionableItems()).containsExactly("actionPlan", "验收标准");
        assertThat(check.weakEvidenceItems()).containsExactly("evidence");
    }

    @ParameterizedTest
    @MethodSource("invalidModelChecks")
    void 模型检查缺少必要字段或评分越界_立即终止(CareerReportCheckResponse model) {
        assertThatThrownBy(() -> CareerDevelopmentReportServiceImpl.mergeIntegrityChecks(CareerDevelopmentReportServiceImpl.checkContentIntegrity(completeReport()), model))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CAREER_REPORT_INVALID));
    }

    static Stream<CareerReportCheckResponse> invalidModelChecks() {
        Stream<CareerReportCheckResponse> scores = Stream.of(-1, 101, null).map(score -> {
            CareerReportCheckResponse check = new CareerReportCheckResponse(true, score, null, null, null, null);

            return check;
        });
        CareerReportCheckResponse missingPassed = new CareerReportCheckResponse(null, 80, null, null, null, null);

        return Stream.concat(Stream.of(null, missingPassed), scores);
    }

    private static Map<String, Object> completeReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("careerExploration", "岗位分析");
        report.put("careerGoals", List.of("目标"));
        report.put("careerPath", List.of("路径"));
        report.put("actionPlan", Map.of(
                "shortTerm", List.of(Map.of("cycle", "两周", "deliverable", "作品")),
                "midTerm", List.of(Map.of("cycle", "四周", "deliverable", "投递清单"))));
        report.put("evaluationPlan", Map.of("cycle", "双周"));
        report.put("evidence", Map.of("technicalEvidence", List.of("项目经历")));
        return report;
    }
}

package com.zdmj.careerReportService.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.careerReportService.dto.CareerReportCheckDTO;
import com.zdmj.careerReportService.dto.CareerReportDTO;
import com.zdmj.careerReportService.dto.CareerReportGenerateRequest;
import com.zdmj.careerReportService.dto.CareerReportUpdateRequest;
import com.zdmj.careerReportService.entity.CareerDevelopmentReport;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.jobService.dto.JobCapabilityProfileDTO;
import com.zdmj.jobService.dto.JobCareerGraphDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.entity.JobCareerGraph;
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
import com.zdmj.matchService.dto.JobStudentMatchDTO;
import com.zdmj.matchService.entity.JobStudentMatch;
import com.zdmj.matchService.service.JobStudentMatchService;
import com.zdmj.resumeService.dto.StudentCapabilityProfileDTO;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CareerDevelopmentReportServiceImplTest {

    @Mock
    private ChatUtil chatUtil;
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
    private EmbeddingModel embeddingModel;

    private CareerDevelopmentReportServiceImpl service;

    @BeforeEach
    void setUp() {
        UserHolder.set(UserContext.of(1L, "u1"));
        service = spy(new CareerDevelopmentReportServiceImpl(
                new ObjectMapper(),
                chatUtil,
                jobService,
                jobCapabilityProfileService,
                jobCareerGraphService,
                jobStudentMatchService,
                studentCapabilityProfileService,
                knowledgeBasesService,
                knowledgeVectorMapper,
                knowledgeDocumentMapper,
                knowledgeEmbeddingService,
                embeddingModel));
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

        JobStudentMatch matchEntity = new JobStudentMatch();
        matchEntity.setId(300L);
        doReturn(matchEntity).when(jobStudentMatchService)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<JobStudentMatch>>any());
        JobCareerGraph graphEntity = new JobCareerGraph();
        graphEntity.setId(400L);
        doReturn(graphEntity).when(jobCareerGraphService)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<JobCareerGraph>>any());
        doReturn(null).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());
        doAnswer(invocation -> {
            CareerDevelopmentReport entity = invocation.getArgument(0);
            entity.setId(999L);
            return true;
        }).when(service).save(any(CareerDevelopmentReport.class));

        doAnswer(invocation -> {
            Class<?> outputClass = invocation.getArgument(3);
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
        }).when(chatUtil).chatStructuredOnce(any(), any(), any(), any());

        CareerReportDTO dto = service.generate(jobId, new CareerReportGenerateRequest());

        assertNotNull(dto);
        assertEquals(999L, dto.getId());
        assertEquals(1, dto.getVersion());
        assertTrue(dto.getCompletenessScore() >= 60);
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

        when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(new float[] {0.1f, 0.2f});
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

        JobStudentMatch matchEntity = new JobStudentMatch();
        matchEntity.setId(300L);
        doReturn(matchEntity).when(jobStudentMatchService)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<JobStudentMatch>>any());
        JobCareerGraph graphEntity = new JobCareerGraph();
        graphEntity.setId(400L);
        doReturn(graphEntity).when(jobCareerGraphService)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<JobCareerGraph>>any());
        doReturn(null).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());
        doAnswer(invocation -> {
            CareerDevelopmentReport entity = invocation.getArgument(0);
            entity.setId(1000L);
            return true;
        }).when(service).save(any(CareerDevelopmentReport.class));

        doAnswer(invocation -> {
            Class<?> outputClass = invocation.getArgument(3);
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
        }).when(chatUtil).chatStructuredOnce(any(), any(), any(), any());

        CareerReportDTO dto = service.generate(jobId, new CareerReportGenerateRequest());

        assertNotNull(dto);
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
        entity.setVersion(3);
        entity.setIsLatest(true);
        entity.setKnowledgeSources(new ObjectMapper().writeValueAsString(List.of(
                Map.of("documentId", 77, "title", "文档 #77", "score", 0.71, "metadata", Map.of()))));

        doReturn(entity).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());

        CareerReportDTO dto = service.getLatestOrNull(10L);

        assertNotNull(dto);
        assertEquals("测试开发学习路线", dto.getKnowledgeSources().get(0).get("title"));
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
        doReturn(true).when(service).updateById(any(CareerDevelopmentReport.class));
        doThrow(new RuntimeException("llm failed"))
                .when(chatUtil).chatStructuredOnce(any(), any(), eq(null), eq(CareerReportCheckDTO.class));

        CareerReportCheckDTO check = service.checkIntegrity(20L);

        assertFalse(check.getPassed());
        assertTrue(check.getMissingSections().contains("职业探索"));
        verify(service).updateById(any(CareerDevelopmentReport.class));
    }

    @Test
    void saveManualEdit_shouldCreateNewVersion() {
        CareerDevelopmentReport existing = new CareerDevelopmentReport();
        existing.setId(31L);
        existing.setUserId(1L);
        existing.setJobId(11L);
        existing.setVersion(2);
        existing.setIsLatest(true);
        existing.setReportContent("{\"careerExploration\":\"old\"}");
        doReturn(existing, existing).when(service)
                .getOne(org.mockito.ArgumentMatchers.<Wrapper<CareerDevelopmentReport>>any());
        doReturn(true).when(service).updateById(any(CareerDevelopmentReport.class));
        doAnswer(invocation -> {
            CareerDevelopmentReport e = invocation.getArgument(0);
            e.setId(32L);
            return true;
        }).when(service).save(any(CareerDevelopmentReport.class));

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

        CareerReportDTO result = service.saveManualEdit(31L, req);

        assertEquals(3, result.getVersion());
        assertEquals(32L, result.getId());
    }

    private static JobListItemDTO buildJob() {
        JobListItemDTO dto = new JobListItemDTO();
        dto.setId(10L);
        dto.setJobName("Java后端");
        dto.setDescription("负责后端开发");
        dto.setCompanyName("示例公司");
        dto.setKeywords(List.of("Java", "Spring"));
        return dto;
    }

    private static StudentCapabilityProfileDTO buildStudentProfile() {
        StudentCapabilityProfileDTO dto = new StudentCapabilityProfileDTO();
        dto.setProfessionalSkills("Java, Spring");
        dto.setLearningAbility("较强");
        return dto;
    }

    private static JobCapabilityProfileDTO buildJobProfile() {
        JobCapabilityProfileDTO dto = new JobCapabilityProfileDTO();
        dto.setProfessionalSkills("Java/Spring");
        dto.setTargetRoleType("java-backend");
        return dto;
    }

    private static JobStudentMatchDTO buildMatch() {
        JobStudentMatchDTO dto = new JobStudentMatchDTO();
        dto.setOverallScore(78);
        return dto;
    }

    private static JobCareerGraphDTO buildGraph() {
        JobCareerGraphDTO dto = new JobCareerGraphDTO();
        dto.setSummary("路径清晰");
        return dto;
    }
}

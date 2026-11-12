package com.zdmj.matchService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.model.PageDTO;
import com.zdmj.jobService.api.JobRole;
import com.zdmj.jobService.api.JobRoleDetectionResult;
import com.zdmj.jobService.api.JobRoleDetectionService;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.service.JobService;
import com.zdmj.matchService.dto.DimensionMatchResponse;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.dto.JobStudentMatchListItemResponse;
import com.zdmj.matchService.entity.JobStudentMatch;
import com.zdmj.matchService.enums.MatchDimension;
import com.zdmj.matchService.mapper.JobStudentMatchMapper;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobStudentMatchServiceImplTest {

    private CurrentActor actor = new CurrentActor(1L);

    private static final Long USER_ID = 1024L;

    @Mock
    private JobService jobService;
    @Mock
    private JobRoleDetectionService roleDetectionService;
    @Mock
    private StudentCapabilityProfileService studentCapabilityProfileService;
    @Mock
    private ModelGateway modelGateway;
    @Mock
    private JobStudentMatchMapper matchMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    private JobStudentMatchServiceImpl matchService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(org.mockito.Mockito.mock(TransactionStatus.class));
        matchService = spy(new JobStudentMatchServiceImpl(
                jobService,
                roleDetectionService,
                studentCapabilityProfileService,
                modelGateway,
                new ObjectMapper(),
                new TransactionTemplate(transactionManager)));
        ReflectionTestUtils.setField(matchService, "baseMapper", matchMapper);
        actor = new CurrentActor(USER_ID);
    }

    @Test
    void generate_promptRouting_shouldUseDefaultPrompt_forUnknownRole() {
        Long jobId = 12L;
        wireHappyPath(jobId, "weird-unknown-role", buildAiResult());

        matchService.generate(actor, jobId, null);

        verify(modelGateway).generateStructured(eq(new CurrentActor(USER_ID)), argThat((ModelRequest req) -> req.promptVars() == null
                        && "job-student-match/default".equals(req.promptName())), eq(JobStudentMatchResponse.class));
        verify(matchMapper).insert(argThat(match -> USER_ID.equals(match.getUserId()) && jobId.equals(match.getJobId())));
    }

    @Test
    void generate_userMessage_shouldInlineWeightsAndKeywords() {
        Long jobId = 15L;
        wireHappyPath(jobId, "java-backend", buildAiResult());

        matchService.generate(actor, jobId, null);

        ArgumentCaptor<ModelRequest> userMessageCaptor =
                ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelGateway).generateStructured(eq(new CurrentActor(USER_ID)), userMessageCaptor.capture(), eq(JobStudentMatchResponse.class));
        assertNull(userMessageCaptor.getValue().promptVars());
        assertEquals("job-student-match/java-backend", userMessageCaptor.getValue().promptName());

        String userMessage = userMessageCaptor.getValue().message();
        assertTrue(userMessage.contains("权重配置"),
                "userMessage 必须内联权重配置；否则 LLM 拿不到打分依据：" + userMessage);
        assertTrue(userMessage.contains("岗位关键词"),
                "userMessage 必须内联岗位关键词；否则 LLM 没法做命中判断：" + userMessage);
        assertTrue(userMessage.contains("开发业务接口"),
                "userMessage 必须内联岗位描述：" + userMessage);
        assertTrue(userMessage.contains("Spring") && userMessage.contains("MySQL"),
                "userMessage 必须包含具体的岗位关键词列表内容：" + userMessage);
    }

    @Test
    void generate_modelGatewayThrows_shouldThrow11001_matchGenerationFailed() {
        Long jobId = 21L;
        prepareJobAndProfiles(jobId, "java-backend");
        doThrow(new RuntimeException("llm down")).when(modelGateway)
                .generateStructured(any(), argThat((ModelRequest req) -> req.promptVars() == null), eq(JobStudentMatchResponse.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchService.generate(actor, jobId, null));
        assertEquals(ErrorCode.MATCH_GENERATION_FAILED.getCode(), ex.getCode());
        verify(matchMapper, never()).insert(any(JobStudentMatch.class));
    }

    @Test
    void generate_modelGatewayReturnsNull_shouldThrow11001_matchGenerationFailed() {
        Long jobId = 22L;
        prepareJobAndProfiles(jobId, "java-backend");
        doReturn(null).when(modelGateway).generateStructured(any(), argThat((ModelRequest req) -> req.promptVars() == null), eq(JobStudentMatchResponse.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchService.generate(actor, jobId, null));
        assertEquals(ErrorCode.MATCH_GENERATION_FAILED.getCode(), ex.getCode());
        verify(matchMapper, never()).insert(any(JobStudentMatch.class));
    }

    @Test
    void generate_studentProfileMissing_shouldThrow11002_preconditionMissing() {
        Long jobId = 23L;
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(null).when(studentCapabilityProfileService).getCurrentUserProfileOrNull(actor);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchService.generate(actor, jobId, null));
        assertEquals(ErrorCode.MATCH_PRECONDITION_MISSING.getCode(), ex.getCode());
        org.mockito.Mockito.verifyNoInteractions(roleDetectionService);
        verify(modelGateway, never()).generateStructured(any(), any(ModelRequest.class), any());
        verify(matchMapper, never()).insert(any(JobStudentMatch.class));
    }

    @Test
    void generate_jobDetailMissing_shouldThrow10001_jobNotFound() {
        Long jobId = 24L;
        doReturn(null).when(jobService).getDetail(jobId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchService.generate(actor, jobId, null));
        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(matchMapper, never()).insert(any(JobStudentMatch.class));
    }

    @Test
    void generate_jobRemovedBeforeSave_shouldThrow10001AndSkipSave() {
        Long jobId = 25L;
        doReturn(buildJobDetail()).doReturn(null).when(jobService).getDetail(jobId);
        stubRole("java-backend");
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService)
                .getCurrentUserProfileOrNull(actor);
        doReturn(buildAiResult()).when(modelGateway).generateStructured(any(), any(ModelRequest.class), any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchService.generate(actor, jobId, null));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(matchMapper, never()).insert(any(JobStudentMatch.class));
    }

    // ========================================================
    // getOrNull / getDefaultWeights 轻量校验
    // ========================================================

    @Test
    void getOrNull_whenNotFound_shouldReturnNull() {
        doReturn(null).when(matchService).getOne(any(LambdaQueryWrapper.class));

        JobStudentMatchResponse dto = matchService.getOrNull(actor, 99L);

        assertNull(dto);
    }

    @Test
    void getOrNull_whenJsonColumnsBlank_shouldReturnEmptyDimensions() {
        JobStudentMatch entity = new JobStudentMatch();
        entity.setJobId(99L);
        entity.setDimensionDetail("  ");
        entity.setWeights(null);
        doReturn(entity).when(matchService).getOne(any(LambdaQueryWrapper.class));

        JobStudentMatchResponse dto = matchService.getOrNull(actor, 99L);

        assertTrue(dto.getDimensions().isEmpty());
        assertNull(dto.getWeights());
    }

    @Test
    void getOrNull_whenDimensionDetailInvalid_shouldThrow() {
        JobStudentMatch entity = new JobStudentMatch();
        entity.setJobId(99L);
        entity.setDimensionDetail("{bad-json");
        doReturn(entity).when(matchService).getOne(any(LambdaQueryWrapper.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> matchService.getOrNull(actor, 99L));

        assertInstanceOf(JsonProcessingException.class, ex.getCause());
    }

    @Test
    void generate_whenDimensionSerializationFails_shouldThrowAndSkipSave() {
        Long jobId = 24L;
        prepareJobAndProfiles(jobId, "java-backend");
        doReturn(buildAiResult()).when(modelGateway).generateStructured(any(), any(ModelRequest.class), any());
        ReflectionTestUtils.setField(matchService, "objectMapper", new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                if (value instanceof Map<?, ?>) {
                    throw new JsonWriteFailure("dimension");
                }
                return super.writeValueAsString(value);
            }
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> matchService.generate(actor, jobId, null));

        assertInstanceOf(JsonProcessingException.class, ex.getCause());
        verify(matchMapper, never()).insert(any(JobStudentMatch.class));
        verify(matchMapper, never()).updateById(any(JobStudentMatch.class));
    }

    @Test
    void getDefaultWeights_forFrontendJob_shouldReturnFrontendDefaults() {
        Long jobId = 31L;
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        stubRole("frontend");

        var weights = matchService.getDefaultWeights(actor, jobId);

        // FRONTEND 默认 0.20 / 0.45 / 0.15 / 0.20，总和 1.0
        assertEquals(0, weights.getProfessionalSkill().compareTo(new java.math.BigDecimal("0.45")));
    }

    @Test
    void getMyPage_shouldNormalizePagingAndDelegateToMapper() {
        JobStudentMatchListItemResponse item = new JobStudentMatchListItemResponse();
        item.setId(1L);
        item.setJobId(11L);
        item.setJobName("Java 后端");
        item.setCompanyName("ZDMJ");
        item.setOverallScore(82);
        item.setKeySkillMatchRate(new BigDecimal("0.7500"));
        item.setSummary("可投递");
        item.setUpdatedAt(LocalDateTime.of(2026, 8, 13, 12, 0));

        Page<JobStudentMatchListItemResponse> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of(item));
        mpPage.setTotal(1);
        doReturn(mpPage).when(matchMapper).selectMyMatchPage(any(Page.class), eq(USER_ID));

        PageDTO<JobStudentMatchListItemResponse> page = matchService.getMyPage(actor, null, null);

        assertEquals(1, page.getPage());
        assertEquals(20, page.getLimit());
        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getList().size());
        assertEquals(11L, page.getList().get(0).getJobId());
        verify(matchMapper).selectMyMatchPage(any(Page.class), eq(USER_ID));
    }

    @Test
    void getMyPage_whenLimitTooLarge_shouldCapAt100() {
        Page<JobStudentMatchListItemResponse> mpPage = new Page<>(1, 100);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0);
        doReturn(mpPage).when(matchMapper).selectMyMatchPage(any(Page.class), eq(USER_ID));

        PageDTO<JobStudentMatchListItemResponse> page = matchService.getMyPage(actor, 1, 500);

        assertEquals(100, page.getLimit());
        assertTrue(page.getList().isEmpty());
        verify(matchMapper).selectMyMatchPage(any(Page.class), eq(USER_ID));
    }

    @Test
    void saveMatch_whenAbsent_shouldInsert() {
        JobStudentMatch draft = ownedDraft();
        doReturn(null).when(matchMapper).selectOne(any());
        doReturn(1).when(matchMapper).insert(draft);

        assertSame(draft, matchService.saveMatch(draft));
        verify(matchMapper, never()).updateById(any(JobStudentMatch.class));
    }

    @Test
    void saveMatch_whenOwnedRowExists_shouldUpdateThatRow() {
        JobStudentMatch draft = ownedDraft();
        JobStudentMatch existing = ownedDraft();
        existing.setId(9L);
        doReturn(existing).when(matchMapper).selectOne(any());
        doReturn(1).when(matchMapper).updateById(draft);

        JobStudentMatch saved = matchService.saveMatch(draft);

        assertEquals(9L, saved.getId());
        verify(matchMapper, never()).insert(any(JobStudentMatch.class));
    }

    @Test
    void saveMatch_whenInsertRaces_shouldUpdateTheCommittedRow() {
        JobStudentMatch draft = ownedDraft();
        JobStudentMatch raced = ownedDraft();
        raced.setId(11L);
        doReturn(null).doReturn(raced).when(matchMapper).selectOne(any());
        doThrow(new DuplicateKeyException("duplicate")).when(matchMapper).insert(draft);
        doReturn(1).when(matchMapper).updateById(draft);

        JobStudentMatch saved = matchService.saveMatch(draft);

        assertEquals(11L, saved.getId());
        verify(matchMapper).updateById(draft);
    }

    @Test
    void saveMatch_whenUserMissing_shouldRejectBeforeQuery() {
        JobStudentMatch draft = ownedDraft();
        draft.setUserId(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> matchService.saveMatch(draft));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(matchMapper, never()).selectOne(any());
    }

    // ========================================================
    // 辅助构造
    // ========================================================

    /**
     * 串联 happy path：岗位与画像就绪，模型返回结果，短事务中插入新匹配记录。
     */
    private void wireHappyPath(Long jobId, String roleType, JobStudentMatchResponse aiResult) {
        prepareJobAndProfiles(jobId, roleType);
        doReturn(aiResult).when(modelGateway).generateStructured(any(), argThat((ModelRequest req) -> req.promptVars() == null), eq(JobStudentMatchResponse.class));
        doReturn(null).when(matchMapper).selectOne(any());
        doReturn(1).when(matchMapper).insert(any(JobStudentMatch.class));
    }

    private void prepareJobAndProfiles(Long jobId, String roleType) {
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        stubRole(roleType);
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService)
                .getCurrentUserProfileOrNull(actor);
    }

    private void stubRole(String roleType) {
        doReturn(new JobRoleDetectionResult(JobRole.fromString(roleType), 0.8, "测试识别"))
                .when(roleDetectionService).detect(any(), any());
    }

    private static final class JsonWriteFailure extends JsonProcessingException {
        private JsonWriteFailure(String message) {
            super(message);
        }
    }

    private static JobStudentMatch ownedDraft() {
        JobStudentMatch draft = new JobStudentMatch();
        draft.setUserId(7L);
        draft.setJobId(3L);
        draft.setOverallScore(80);
        return draft;
    }

    private static JobListItemResponse buildJobDetail() {
        JobListItemResponse dto = new JobListItemResponse();
        dto.setJobName("Java 后端开发");
        dto.setCompanyName("ZDMJ");
        dto.setDescription("Java + Spring + MySQL + Redis\n开发业务接口\n维护线上服务\n熟悉 Spring Boot\n熟悉 MySQL 索引");
        dto.setLocation("深圳");
        dto.setKeywords(List.of("Java", "Spring", "MySQL", "Redis"));
        return dto;
    }

    private static StudentCapabilityProfileResponse buildStudentProfile() {
        StudentCapabilityProfileResponse p = new StudentCapabilityProfileResponse();
        p.setProfessionalSkills("Java、Spring Boot、MySQL");
        p.setHonorsAndAwards("蓝桥杯省赛二等奖");
        p.setInnovationAbility("校赛二等奖");
        p.setLearningAbility("自学 Redis");
        p.setPressureResistance("曾在比赛 48h 内交付");
        p.setCommunicationAbility("社团组织活动");
        p.setPracticalAbility("实习 3 个月");
        p.setSummary("Java 方向应届生");
        p.setStrengths(List.of("基础扎实"));
        StudentCapabilityProfileResponse.Suggestion gap = new StudentCapabilityProfileResponse.Suggestion();
        gap.setCategory("技能缺失");
        gap.setIssue("缺少 Kafka 相关实践");
        gap.setRecommendation("在项目中补充消息队列场景");
        p.setSuggestions(List.of(gap));
        return p;
    }

    private static JobStudentMatchResponse buildAiResult() {
        JobStudentMatchResponse dto = new JobStudentMatchResponse();
        dto.setTargetRoleType("java-backend");
        Map<String, DimensionMatchResponse> dims = new LinkedHashMap<>();
        for (MatchDimension d : MatchDimension.values()) {
            DimensionMatchResponse dim = new DimensionMatchResponse();
            dim.setJobSide("job-" + d.getCode());
            dim.setStudentSide("stu-" + d.getCode());
            dim.setScore(70);
            dim.setGap("gap-" + d.getCode());
            dim.setEvidence(List.of("e1", "e2"));
            dims.put(d.getCode(), dim);
        }
        dto.setDimensions(dims);
        dto.setMatchedHighlights(List.of("Java 基础扎实"));
        dto.setCriticalGaps(List.of("缺少消息队列经验"));
        dto.setMatchedKeywords(List.of("Java", "Spring", "MySQL"));
        dto.setMissingKeywords(List.of("Redis"));
        dto.setSummary("整体可投递");
        return dto;
    }
}

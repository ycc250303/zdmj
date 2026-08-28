package com.zdmj.matchService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.model.PageDTO;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobService;
import com.zdmj.matchService.dto.DimensionMatchResponse;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.dto.JobStudentMatchListItemResponse;
import com.zdmj.matchService.entity.JobStudentMatch;
import com.zdmj.matchService.enums.MatchDimension;
import com.zdmj.matchService.mapper.JobStudentMatchMapper;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link JobStudentMatchServiceImpl} 单元测试。
 *
 * <p>重点回归：「队友报障 —— 换岗位仍然 11001」。该 bug 的根因是
 * {@code generate(...)} 当时传入了非空 {@code promptVars}，触发 Spring AI
 * {@code PromptTemplate.render(...)}（基于 StringTemplate）解析提示词里的 JSON 示例花括号失败，
 * 异常被 {@code catch (Exception e)} 吞成 11001 错误码，并且对任何岗位都会一致触发。</p>
 *
 * <p>本类测试 {@code generate(...)} 的全部正反路径，并显式断言「
 * {@link ChatUtil#chatStructuredOnce} 必须以 {@code null} promptVars 调用」，
 * 以保证未来不会再走 PromptTemplate 渲染分支。</p>
 */
@ExtendWith(MockitoExtension.class)
class JobStudentMatchServiceImplTest {

    private static final Long USER_ID = 1024L;

    @Mock
    private JobService jobService;
    @Mock
    private JobCapabilityProfileService jobCapabilityProfileService;
    @Mock
    private StudentCapabilityProfileService studentCapabilityProfileService;
    @Mock
    private ChatUtil chatUtil;
    @Mock
    private JobStudentMatchMapper matchMapper;

    private JobStudentMatchServiceImpl matchService;

    @BeforeEach
    void setUp() {
        matchService = spy(new JobStudentMatchServiceImpl(
                jobService,
                jobCapabilityProfileService,
                studentCapabilityProfileService,
                chatUtil,
                new ObjectMapper()));
        ReflectionTestUtils.setField(matchService, "baseMapper", matchMapper);
        UserHolder.set(new UserContext(USER_ID, "tester", "tester@example.com"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    // ========================================================
    // 核心回归：必须以 null promptVars 调 chatStructuredOnce
    // ========================================================

    @Test
    void generate_shouldPassNullPromptVarsToChatUtil_toAvoidSTTemplateRender() {
        Long jobId = 11L;
        wireHappyPath(jobId, "java-backend", buildAiResult());

        JobStudentMatchResponse result = matchService.generate(jobId, null);

        assertNotNull(result);
        // 关键断言：promptVars 必须是 null —— 一旦改回 Map，PromptTemplate 渲染会因
        // 提示词正文里的 JSON 大括号炸成 STException，被 catch 吞成 11001。
        verify(chatUtil).chatStructuredOnce(
                eq(USER_ID),
                any(String.class),
                eq("job-student-match/java-backend"),
                isNull(),
                eq(JobStudentMatchResponse.class));
    }

    @Test
    void generate_promptRouting_shouldUseDefaultPrompt_forUnknownRole() {
        Long jobId = 12L;
        wireHappyPath(jobId, "weird-unknown-role", buildAiResult());

        matchService.generate(jobId, null);

        verify(chatUtil).chatStructuredOnce(
                eq(USER_ID),
                any(String.class),
                eq("job-student-match/default"),
                isNull(),
                eq(JobStudentMatchResponse.class));
    }

    @Test
    void generate_promptRouting_shouldUseFrontendPrompt_forFrontendRole() {
        Long jobId = 13L;
        wireHappyPath(jobId, "frontend", buildAiResult());

        matchService.generate(jobId, null);

        verify(chatUtil).chatStructuredOnce(
                eq(USER_ID),
                any(String.class),
                eq("job-student-match/frontend"),
                isNull(),
                eq(JobStudentMatchResponse.class));
    }

    @Test
    void generate_promptRouting_shouldUseAiAgentPrompt_forAiAgentRole() {
        Long jobId = 14L;
        wireHappyPath(jobId, "ai-agent", buildAiResult());

        matchService.generate(jobId, null);

        verify(chatUtil).chatStructuredOnce(
                eq(USER_ID),
                any(String.class),
                eq("job-student-match/ai-agent"),
                isNull(),
                eq(JobStudentMatchResponse.class));
    }

    // ========================================================
    // userMessage 必须内联权重 + 关键词（替代被移除的 promptVars）
    // ========================================================

    @Test
    void generate_userMessage_shouldInlineWeightsAndKeywords_sinceWeNoLongerUsePromptVars() {
        Long jobId = 15L;
        wireHappyPath(jobId, "java-backend", buildAiResult());

        matchService.generate(jobId, null);

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatUtil).chatStructuredOnce(
                eq(USER_ID),
                userMessageCaptor.capture(),
                eq("job-student-match/java-backend"),
                isNull(),
                eq(JobStudentMatchResponse.class));

        String userMessage = userMessageCaptor.getValue();
        // 这俩内容原本是通过 promptVars 注入到 system prompt 的；
        // 现在改为直接拼接进 user message，确保 LLM 仍能看到。
        org.junit.jupiter.api.Assertions.assertTrue(
                userMessage.contains("权重配置"),
                "userMessage 必须内联权重配置；否则 LLM 拿不到打分依据：" + userMessage);
        org.junit.jupiter.api.Assertions.assertTrue(
                userMessage.contains("岗位关键词"),
                "userMessage 必须内联岗位关键词；否则 LLM 没法做命中判断：" + userMessage);
        org.junit.jupiter.api.Assertions.assertTrue(
                userMessage.contains("Spring") && userMessage.contains("MySQL"),
                "userMessage 必须包含具体的岗位关键词列表内容：" + userMessage);
    }

    // ========================================================
    // 异常分支：LLM 故障 → 11001（保留既有行为）
    // ========================================================

    @Test
    void generate_chatUtilThrows_shouldThrow11001_matchGenerationFailed() {
        Long jobId = 21L;
        prepareJobAndProfiles(jobId, "java-backend");
        doThrow(new RuntimeException("llm down")).when(chatUtil)
                .chatStructuredOnce(anyLong(), any(String.class), any(String.class), isNull(),
                        eq(JobStudentMatchResponse.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchService.generate(jobId, null));
        assertEquals(ErrorCode.MATCH_GENERATION_FAILED.getCode(), ex.getCode());
    }

    @Test
    void generate_chatUtilReturnsNull_shouldThrow11001_matchGenerationFailed() {
        Long jobId = 22L;
        prepareJobAndProfiles(jobId, "java-backend");
        doReturn(null).when(chatUtil).chatStructuredOnce(
                anyLong(), any(String.class), any(String.class), isNull(),
                eq(JobStudentMatchResponse.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchService.generate(jobId, null));
        assertEquals(ErrorCode.MATCH_GENERATION_FAILED.getCode(), ex.getCode());
    }

    @Test
    void generate_studentProfileMissing_shouldThrow11002_preconditionMissing() {
        Long jobId = 23L;
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(buildJobProfile("java-backend")).when(jobCapabilityProfileService)
                .getJobCapabilityProfileOrNull(jobId);
        doReturn(null).when(studentCapabilityProfileService).getCurrentUserProfileOrNull();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchService.generate(jobId, null));
        assertEquals(ErrorCode.MATCH_PRECONDITION_MISSING.getCode(), ex.getCode());
    }

    @Test
    void generate_jobDetailMissing_shouldThrow10001_jobNotFound() {
        Long jobId = 24L;
        doReturn(null).when(jobService).getDetail(jobId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchService.generate(jobId, null));
        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========================================================
    // getOrNull / getDefaultWeights 轻量校验
    // ========================================================

    @Test
    void getOrNull_whenNotFound_shouldReturnNull() {
        doReturn(null).when(matchService).getOne(any(LambdaQueryWrapper.class));

        JobStudentMatchResponse dto = matchService.getOrNull(99L);

        assertNull(dto);
    }

    @Test
    void getDefaultWeights_forFrontendJob_shouldReturnFrontendDefaults() {
        Long jobId = 31L;
        doReturn(buildJobProfile("frontend")).when(jobCapabilityProfileService)
                .getJobCapabilityProfileOrNull(jobId);

        var weights = matchService.getDefaultWeights(jobId);

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

        PageDTO<JobStudentMatchListItemResponse> page = matchService.getMyPage(null, null);

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

        PageDTO<JobStudentMatchListItemResponse> page = matchService.getMyPage(1, 500);

        assertEquals(100, page.getLimit());
        assertTrue(page.getList().isEmpty());
        verify(matchMapper).selectMyMatchPage(any(Page.class), eq(USER_ID));
    }

    // ========================================================
    // 辅助构造
    // ========================================================

    /**
     * 串联 happy path：JobDetail + JobProfile + StudentProfile + aiResult + getOne=null + save=true。
     */
    private void wireHappyPath(Long jobId, String roleType, JobStudentMatchResponse aiResult) {
        prepareJobAndProfiles(jobId, roleType);
        doReturn(aiResult).when(chatUtil).chatStructuredOnce(
                anyLong(), any(String.class), any(String.class), isNull(), eq(JobStudentMatchResponse.class));
        doReturn(null).when(matchService).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(matchService).save(any(JobStudentMatch.class));
    }

    private void prepareJobAndProfiles(Long jobId, String roleType) {
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(buildJobProfile(roleType)).when(jobCapabilityProfileService)
                .getJobCapabilityProfileOrNull(jobId);
        doReturn(buildStudentProfile()).when(studentCapabilityProfileService)
                .getCurrentUserProfileOrNull();
    }

    private static JobListItemResponse buildJobDetail() {
        JobListItemResponse dto = new JobListItemResponse();
        dto.setJobName("Java 后端开发");
        dto.setCompanyName("ZDMJ");
        dto.setDescription("Java + Spring + MySQL + Redis");
        dto.setLocation("深圳");
        dto.setJobDuties(List.of("开发业务接口", "维护线上服务"));
        dto.setJobRequirements(List.of("熟悉 Spring Boot", "熟悉 MySQL 索引"));
        dto.setKeywords(List.of("Java", "Spring", "MySQL", "Redis"));
        return dto;
    }

    private static JobCapabilityProfileResponse buildJobProfile(String roleType) {
        JobCapabilityProfileResponse p = new JobCapabilityProfileResponse();
        p.setTargetRoleType(roleType);
        p.setProfessionalSkills("Java/Spring/MySQL/Redis");
        p.setCertificates("无");
        p.setInnovationAbility("有持续改进意识");
        p.setLearningAbility("快速跟进新框架");
        p.setPressureResistance("能承担线上稳定性");
        p.setCommunicationAbility("能与产品/前端协作");
        p.setPracticalAbility("有实习/项目落地经验");
        p.setSummary("Java 后端校招岗位");
        p.setStrengths(List.of("工程化能力扎实"));
        return p;
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

package com.zdmj.jobService.service.impl;

import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.PromptUtil.JobRole;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JobAnalysisSupportTest {

    @Mock
    private ChatUtil chatUtil;

    @Mock
    private Logger logger;

    @Test
    void detectRole_whenEmptyText_shouldReturnUnknown() {
        Map<JobRole, List<String>> keywords = Map.of(JobRole.JAVA, List.of("java", "spring"));

        JobRole role = JobAnalysisSupport.detectRole("", chatUtil, keywords, 4, logger);

        assertEquals(JobRole.UNKNOWN, role);
        verify(chatUtil, never()).chatStructuredOnce(any(), any(), eq(null), any());
    }

    @Test
    void detectRole_whenLlmFail_shouldFallbackToKeywordBestRole() {
        Map<JobRole, List<String>> keywords = Map.of(
                JobRole.JAVA, List.of("java", "spring"),
                JobRole.FRONTEND, List.of("react"));
        String text = "需要 Java 与 Spring 能力，熟悉微服务";
        doThrow(new RuntimeException("llm unavailable")).when(chatUtil)
                .chatStructuredOnce(any(), any(), eq(null), any());

        JobRole role = JobAnalysisSupport.detectRole(text, chatUtil, keywords, 4, logger);

        assertEquals(JobRole.JAVA, role);
        verify(chatUtil).chatStructuredOnce(any(), any(), eq(null), any());
    }

    @Test
    void profile_estimateRoleConfidence_whenUnknown_shouldReturnLowConfidence() {
        double score = JobAnalysisSupport.estimateRoleConfidence(JobRole.UNKNOWN, "java spring",
                Map.of(JobRole.JAVA, List.of("java")));

        assertEquals(0.2, score);
        verify(logger, never()).warn(any());
    }

    @Test
    void profile_estimateRoleConfidence_whenKeywordHit_shouldIncreaseScore() {
        double score = JobAnalysisSupport.estimateRoleConfidence(
                JobRole.JAVA,
                "java spring redis mysql mybatis jvm",
                Map.of(JobRole.JAVA, List.of("java", "spring", "redis", "mysql", "mybatis", "jvm")));

        assertEquals(0.95, score);
        verify(logger, never()).warn(any());
    }

    @Test
    void profile_estimateRoleConfidence_whenNoKeywordHit_shouldReturnBaseScore() {
        double score = JobAnalysisSupport.estimateRoleConfidence(
                JobRole.JAVA,
                "python golang",
                Map.of(JobRole.JAVA, List.of("java", "spring")));

        assertEquals(0.35, score);
        verify(logger, never()).warn(any());
    }

    @Test
    void profile_estimateRoleConfidence_whenTextMissing_shouldReturnLowConfidence() {
        double score = JobAnalysisSupport.estimateRoleConfidence(
                JobRole.JAVA,
                "   ",
                Map.of(JobRole.JAVA, List.of("java")));

        assertEquals(0.2, score);
        verify(logger, never()).warn(any());
    }

    @Test
    void graph_buildJobContext_whenFieldsMissing_shouldRenderDefaultText() {
        JobListItemDTO dto = new JobListItemDTO();
        dto.setJobName("  Java开发  ");
        dto.setJobDuties(Collections.emptyList());
        dto.setJobRequirements(List.of(" ", "熟悉Spring"));

        String context = JobAnalysisSupport.buildJobContext(dto, "graph intro");

        assertTrue(context.contains("岗位名称：Java开发"));
        assertTrue(context.contains("公司名称：未提供"));
        assertTrue(context.contains("岗位职责：未提供"));
        assertTrue(context.contains("岗位要求：熟悉Spring"));
    }

    @Test
    void graph_buildJobContext_whenListAllBlank_shouldRenderNotProvided() {
        JobListItemDTO dto = new JobListItemDTO();
        dto.setJobName("后端开发");
        dto.setJobDuties(List.of(" ", "   "));
        dto.setJobRequirements(List.of("", "\t"));
        dto.setKeywords(List.of("  ", "\n"));
        dto.setCompanyIndustries(List.of(" ", ""));

        String context = JobAnalysisSupport.buildJobContext(dto, "graph intro");

        assertTrue(context.contains("岗位职责：未提供"));
        assertTrue(context.contains("岗位要求：未提供"));
        assertTrue(context.contains("关键词：未提供"));
        assertTrue(context.contains("公司行业：未提供"));
        assertFalse(context.contains("；"));
    }

    @Test
    void fail_toJson_whenValueNull_shouldReturnNullWithoutWarn() {
        String json = JobAnalysisSupport.toJson(null, new ObjectMapper(), logger, "serialize fail");

        assertNull(json);
        verifyNoInteractions(logger);
    }

    @Test
    void fail_toJson_whenMapperThrows_shouldReturnNullAndWarn() {
        String json = JobAnalysisSupport.toJson(
                List.of("a"),
                new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) {
                        throw new RuntimeException("json fail");
                    }
                },
                logger,
                "serialize fail");

        assertNull(json);
        verify(logger).warn(eq("{}: {}"), eq("serialize fail"), eq("json fail"));
    }
}

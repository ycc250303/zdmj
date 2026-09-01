package com.zdmj.common.ai;

import com.zdmj.common.ai.prompt.PromptNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobRoleDetectorTest {

    @Mock
    private ChatUtil chatUtil;

    @Mock
    private Logger logger;

    @Test
    void detect_whenEmptyText_shouldReturnUnknownWithoutLlm() {
        JobRoleDetector.DetectResult result = JobRoleDetector.detect(1L, "  ", chatUtil, logger);

        assertEquals(JobRole.UNKNOWN, result.role());
        assertEquals(0.0, result.confidence());
        verify(chatUtil, never()).chatStructuredOnce(any(), any(), any(), eq(null), any());
    }

    @Test
    void detect_whenKeywordDirectHit_shouldSkipLlm() {
        JobRoleDetector.DetectResult result = JobRoleDetector.detect(
                1L, "Java Spring Boot MySQL Redis project", chatUtil, logger);

        assertEquals(JobRole.JAVA, result.role());
        assertTrue(result.reason().contains("关键词"));
        verify(chatUtil, never()).chatStructuredOnce(any(), any(), any(), eq(null), any());
    }

    @Test
    void detect_whenLlmUnknown_shouldFallbackToKeywordWeakHit() {
        String text = "java spring 实习经历";
        JobRoleDetector.RoleDetectLLMResult llm = new JobRoleDetector.RoleDetectLLMResult();
        llm.setRoleCode("unknown");
        llm.setConfidence(0.93);
        llm.setReason("不确定");
        doReturn(llm).when(chatUtil).chatStructuredOnce(
                eq(1L), eq(text), eq(PromptNames.JOB_DETECT), eq(null),
                eq(JobRoleDetector.RoleDetectLLMResult.class));

        JobRoleDetector.DetectResult result = JobRoleDetector.detect(1L, text, chatUtil, logger);

        assertEquals(JobRole.JAVA, result.role());
        assertEquals(0.45, result.confidence());
    }

    @Test
    void detect_whenLlmThrows_shouldFallbackToKeywordBestRole() {
        String text = "需要 Java 与 Spring 能力，熟悉微服务";
        doThrow(new RuntimeException("llm unavailable")).when(chatUtil)
                .chatStructuredOnce(any(), any(), any(), eq(null), any());

        JobRoleDetector.DetectResult result = JobRoleDetector.detect(1L, text, chatUtil, logger);

        assertEquals(JobRole.JAVA, result.role());
        assertEquals(0.35, result.confidence());
        verify(chatUtil).chatStructuredOnce(any(), any(), any(), eq(null), any());
    }

    @Test
    void detect_whenLlmThrowsAndNoKeyword_shouldReturnUnknown() {
        doThrow(new RuntimeException("llm timeout")).when(chatUtil)
                .chatStructuredOnce(any(), any(), any(), eq(null), any());

        JobRoleDetector.DetectResult result = JobRoleDetector.detect(1L, "golang rust", chatUtil, logger);

        assertEquals(JobRole.UNKNOWN, result.role());
        assertEquals(0.2, result.confidence());
    }

    @Test
    void jobDetectPrompt_shouldListEverySlug() throws Exception {
        String content = StreamUtils.copyToString(
                new ClassPathResource("prompts/job-detect.md").getInputStream(),
                StandardCharsets.UTF_8);
        for (JobRole role : JobRole.values()) {
            assertTrue(content.contains(role.slug()),
                    "job-detect.md 缺少 slug: " + role.slug());
        }
    }
}

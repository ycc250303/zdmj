package com.zdmj.jobService;

import com.zdmj.common.ai.JobRole;
import com.zdmj.common.constants.PromptNames;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.StructuredModelRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobRoleDetectorTest {

    @Mock
    private ModelGateway modelGateway;

    @Mock
    private Logger logger;

    @Test
    void detect_whenEmptyText_shouldReturnUnknownWithoutLlm() {
        JobRoleDetector.DetectResult result = JobRoleDetector.detect(1L, "  ", modelGateway, logger);

        assertEquals(JobRole.UNKNOWN, result.role());
        assertEquals(0.0, result.confidence());
        verify(modelGateway, never()).generateStructured(any(), argThat((StructuredModelRequest<?> req) -> req.promptVars() == null));
    }

    @Test
    void detect_whenKeywordDirectHit_shouldSkipLlm() {
        JobRoleDetector.DetectResult result = JobRoleDetector.detect(
                1L, "Java Spring Boot MySQL Redis project", modelGateway, logger);

        assertEquals(JobRole.JAVA, result.role());
        assertTrue(result.reason().contains("关键词"));
        verify(modelGateway, never()).generateStructured(any(), argThat((StructuredModelRequest<?> req) -> req.promptVars() == null));
    }

    @Test
    void detect_whenLlmUnknown_shouldFallbackToKeywordWeakHit() {
        String text = "java spring 实习经历";
        JobRoleDetector.RoleDetectLLMResult llm = new JobRoleDetector.RoleDetectLLMResult();
        llm.setRoleCode("unknown");
        llm.setConfidence(0.93);
        llm.setReason("不确定");
        doReturn(llm).when(modelGateway).generateStructured(
                eq(com.zdmj.common.context.CurrentActor.of(1L)),
                argThat((StructuredModelRequest<?> req) -> PromptNames.JOB_DETECT.equals(req.promptName())
                        && req.promptVars() == null
                        && req.outputType() == JobRoleDetector.RoleDetectLLMResult.class));

        JobRoleDetector.DetectResult result = JobRoleDetector.detect(1L, text, modelGateway, logger);

        assertEquals(JobRole.JAVA, result.role());
        assertEquals(0.45, result.confidence());
    }

    @Test
    void detect_whenLlmThrows_shouldFallbackToKeywordBestRole() {
        String text = "需要 Java 与 Spring 能力，熟悉微服务";
        doThrow(new RuntimeException("llm unavailable")).when(modelGateway)
                .generateStructured(any(), argThat((StructuredModelRequest<?> req) -> req.promptVars() == null));

        JobRoleDetector.DetectResult result = JobRoleDetector.detect(1L, text, modelGateway, logger);

        assertEquals(JobRole.JAVA, result.role());
        assertEquals(0.35, result.confidence());
        verify(modelGateway).generateStructured(any(), argThat((StructuredModelRequest<?> req) -> req.promptVars() == null));
    }

    @Test
    void detect_whenLlmThrowsAndNoKeyword_shouldReturnUnknown() {
        doThrow(new RuntimeException("llm timeout")).when(modelGateway)
                .generateStructured(any(), argThat((StructuredModelRequest<?> req) -> req.promptVars() == null));

        JobRoleDetector.DetectResult result = JobRoleDetector.detect(1L, "rust elixir", modelGateway, logger);

        assertEquals(JobRole.UNKNOWN, result.role());
        assertEquals(0.2, result.confidence());
    }

    @Test
    void detect_whenJavascriptStack_shouldNotCountAsJava() {
        JobRoleDetector.DetectResult result = JobRoleDetector.detect(
                1L, "JavaScript TypeScript React Vue CSS Webpack", modelGateway, logger);

        assertEquals(JobRole.FRONTEND, result.role());
        verify(modelGateway, never()).generateStructured(any(), argThat((StructuredModelRequest<?> req) -> req.promptVars() == null));
    }

    @Test
    void detect_whenJunitStack_shouldDirectHitSoftwareTest() {
        JobRoleDetector.DetectResult result = JobRoleDetector.detect(
                1L, "测试工程师，JUnit + Selenium + Postman，负责缺陷跟踪", modelGateway, logger);

        assertEquals(JobRole.SOFTWARE_TEST, result.role());
        verify(modelGateway, never()).generateStructured(any(), argThat((StructuredModelRequest<?> req) -> req.promptVars() == null));
    }

    @Test
    void containsKeyword_shouldUseAsciiWordBoundary() {
        assertTrue(JobRoleDetector.containsKeyword("java spring boot", "java"));
        assertFalse(JobRoleDetector.containsKeyword("javascript react", "java"));
        assertTrue(JobRoleDetector.containsKeyword("javascript react", "javascript"));
        assertTrue(JobRoleDetector.containsKeyword("熟悉测试与缺陷", "测试"));
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

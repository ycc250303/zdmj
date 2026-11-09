package com.zdmj.jobService.service.impl;

import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.jobService.api.JobRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobRoleDetectionServiceImplTest {
    @Mock ModelGateway gateway;
    private final CurrentActor actor = CurrentActor.of(1L);

    @Test
    void emptyTextDoesNotCallModel() {
        var result = new JobRoleDetectionServiceImpl(gateway).detect(actor, "  ");
        assertEquals(JobRole.UNKNOWN, result.role());
        assertEquals(0, result.confidence());
        verifyNoInteractions(gateway);
    }

    @Test
    void keywordThresholdPreservesScoresAndBoundaries() {
        var service = new JobRoleDetectionServiceImpl(gateway);
        var java = service.detect(actor, "Java Spring Boot MySQL Redis project");
        assertEquals(JobRole.JAVA, java.role());
        assertEquals(0.85, java.confidence(), 0.00001);
        assertEquals(JobRole.FRONTEND, service.detect(actor, "JavaScript TypeScript React Vue CSS Webpack").role());
        assertEquals(JobRole.SOFTWARE_TEST, service.detect(actor, "测试 JUnit Selenium Postman 缺陷").role());
        assertTrue(JobRoleDetectionServiceImpl.containsKeyword("java spring boot", "java"));
        assertFalse(JobRoleDetectionServiceImpl.containsKeyword("javascript react", "java"));
        assertTrue(JobRoleDetectionServiceImpl.containsKeyword("熟悉测试与缺陷", "测试"));
        verifyNoInteractions(gateway);
    }

    private void response(String code, Double confidence) {
        var response = new JobRoleDetectionServiceImpl.RoleDetectLLMResult();
        response.setRoleCode(code);
        response.setConfidence(confidence);
        response.setReason("识别依据");
        doReturn(response).when(gateway).generateStructured(eq(actor), argThat((ModelRequest request) ->
                request.promptName().equals("job-detect") && request.promptVars() == null), eq(JobRoleDetectionServiceImpl.RoleDetectLLMResult.class));
    }

    @Test
    void modelSupportsNormalizedCodesAndUnknown() {
        var service = new JobRoleDetectionServiceImpl(gateway);
        response(" AI_AGENT ", 0.91);
        var result = service.detect(actor, "文本");
        assertEquals(JobRole.AI_AGENT, result.role());
        assertEquals(0.91, result.confidence());
        assertEquals("识别依据", result.reason());
        response("unknown", 0.2);
        assertEquals(JobRole.UNKNOWN, service.detect(actor, "java").role());
        response("default", 0.0);
        assertEquals(JobRole.UNKNOWN, service.detect(actor, "文本").role());
    }

    @Test
    void modelFailurePropagates() {
        var failure = new IllegalStateException("模型不可用");
        doThrow(failure).when(gateway).generateStructured(any(), any(), any());
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new JobRoleDetectionServiceImpl(gateway).detect(actor, "java")));
    }

    @Test
    void malformedResponsesFailImmediately() {
        var service = new JobRoleDetectionServiceImpl(gateway);
        assertThrows(IllegalStateException.class, () -> service.detect(actor, "文本"));
        for (String code : new String[]{null, "", "qa", "unsupported"}) {
            response(code, 0.8);
            assertThrows(IllegalStateException.class, () -> service.detect(actor, "文本"));
        }
        for (Double value : new Double[]{null, -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            response("java-backend", value);
            assertThrows(IllegalStateException.class, () -> service.detect(actor, "文本"));
        }
    }
}

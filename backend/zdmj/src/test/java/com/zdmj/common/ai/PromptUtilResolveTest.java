package com.zdmj.common.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptUtilResolveTest {

    @Test
    void resolve_whenSpecificFileExists_shouldUseSlugPath() {
        PromptUtil promptUtil = new PromptUtil(new DefaultResourceLoader());

        assertEquals("job-requirement/java-backend",
                promptUtil.resolve(PromptScenario.JOB_REQUIREMENT, JobRole.JAVA));
        assertEquals("job-career-graph/cpp",
                promptUtil.resolve(PromptScenario.JOB_CAREER_GRAPH, JobRole.CPP));
        assertEquals("job-student-match/ai-agent",
                promptUtil.resolve(PromptScenario.JOB_STUDENT_MATCH, JobRole.AI_AGENT));
        assertEquals("resume-analysis/default",
                promptUtil.resolve(PromptScenario.RESUME_ANALYSIS, JobRole.UNKNOWN));
        assertEquals("resume-analysis/default",
                promptUtil.resolve(PromptScenario.RESUME_ANALYSIS, null));
    }

    @Test
    void resolve_whenSpecificMissing_shouldFallbackDefault() {
        ResourceLoader loader = mock(ResourceLoader.class);
        Resource missing = mock(Resource.class);
        Resource fallback = mock(Resource.class);
        when(missing.exists()).thenReturn(false);
        when(fallback.exists()).thenReturn(true);
        when(loader.getResource("classpath:prompts/job-requirement/java-backend.md")).thenReturn(missing);
        when(loader.getResource("classpath:prompts/job-requirement/default.md")).thenReturn(fallback);

        PromptUtil promptUtil = new PromptUtil(loader);

        assertEquals("job-requirement/default",
                promptUtil.resolve(PromptScenario.JOB_REQUIREMENT, JobRole.JAVA));
    }

    @Test
    void resolve_whenScenarioNull_shouldThrow() {
        PromptUtil promptUtil = new PromptUtil(new DefaultResourceLoader());
        assertThrows(IllegalArgumentException.class, () -> promptUtil.resolve(null, JobRole.JAVA));
    }

    @Test
    void resolve_allRoles_shouldHitExistingFileForEveryScenario() {
        PromptUtil promptUtil = new PromptUtil(new DefaultResourceLoader());
        for (PromptScenario scenario : PromptScenario.values()) {
            for (JobRole role : JobRole.values()) {
                String name = promptUtil.resolve(scenario, role);
                promptUtil.load(name);
            }
        }
    }
}

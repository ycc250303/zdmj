package com.zdmj.common.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JobRoleTest {

    @Test
    void fromString_shouldNormalizeHyphenUnderscoreAndAliases() {
        assertEquals(JobRole.JAVA, JobRole.fromString("java-backend"));
        assertEquals(JobRole.JAVA, JobRole.fromString("java_backend"));
        assertEquals(JobRole.JAVA, JobRole.fromString(" JAVA "));
        assertEquals(JobRole.JAVA, JobRole.fromString("backend"));
        assertEquals(JobRole.FRONTEND, JobRole.fromString("fe"));
        assertEquals(JobRole.SOFTWARE_TEST, JobRole.fromString("qa"));
        assertEquals(JobRole.AI_AGENT, JobRole.fromString("ai_agent"));
        assertEquals(JobRole.DEVOPS_SRE, JobRole.fromString("sre"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("unknown"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("default"));
    }

    @Test
    void fromString_whenBlankOrUnknown_shouldReturnUnknown() {
        assertEquals(JobRole.UNKNOWN, JobRole.fromString(null));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("  "));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("golang"));
    }

    @Test
    void fromPromptName_shouldUseLastPathSegment() {
        assertEquals(JobRole.JAVA, JobRole.fromPromptName("job-requirement/java-backend"));
        assertEquals(JobRole.FRONTEND, JobRole.fromPromptName("resume-analysis/frontend"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromPromptName("job-career-graph/default"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromPromptName(null));
        assertEquals(JobRole.UNKNOWN, JobRole.fromPromptName("JOB_CAREER_GRAPH_JAVA"));
    }

    @Test
    void slug_shouldMatchPromptFileStem() {
        assertEquals("java-backend", JobRole.JAVA.slug());
        assertEquals("software-test", JobRole.SOFTWARE_TEST.slug());
        assertEquals("default", JobRole.UNKNOWN.slug());
    }

    @Test
    void fromString_shouldBeIdempotentWithSlug() {
        for (JobRole role : JobRole.values()) {
            assertSame(role, JobRole.fromString(role.slug()));
            assertSame(role, JobRole.fromString(role.slug().replace('-', '_')));
        }
    }
}

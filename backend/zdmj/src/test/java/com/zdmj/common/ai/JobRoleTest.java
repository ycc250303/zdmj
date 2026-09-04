package com.zdmj.common.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRoleTest {

    @Test
    void fromString_shouldNormalizeHyphenUnderscoreAndCase() {
        assertEquals(JobRole.JAVA, JobRole.fromString("java-backend"));
        assertEquals(JobRole.JAVA, JobRole.fromString("java_backend"));
        assertEquals(JobRole.JAVA, JobRole.fromString(" Java-Backend "));
        assertEquals(JobRole.AI_AGENT, JobRole.fromString("ai_agent"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("unknown"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("default"));
    }

    @Test
    void fromString_whenShortIndustryCode_shouldReturnUnknown() {
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("java"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("backend"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("fe"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("qa"));
        assertEquals(JobRole.UNKNOWN, JobRole.fromString("sre"));
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

    @Test
    void keywords_competingRoles_shouldBeEqualLengthAndNotNested() {
        assertTrue(JobRole.UNKNOWN.keywords().isEmpty());
        int size = JobRole.JAVA.keywords().size();
        for (JobRole role : JobRole.values()) {
            if (role == JobRole.UNKNOWN) {
                continue;
            }
            List<String> keywords = role.keywords();
            assertEquals(size, keywords.size(), role.name() + " 关键词数量须与其它方向相同");
            for (int i = 0; i < keywords.size(); i++) {
                String current = keywords.get(i).toLowerCase(Locale.ROOT);
                for (int j = 0; j < keywords.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    String other = keywords.get(j).toLowerCase(Locale.ROOT);
                    assertFalse(other.contains(current),
                            role.name() + " 存在同义/包含: " + current + " ⊂ " + other);
                }
            }
        }
    }
}

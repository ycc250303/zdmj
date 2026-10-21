package com.zdmj.jobService.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobRoleDetectionResultTest {
    @Test
    void rejectsInvalidFields() {
        assertThrows(IllegalArgumentException.class, () -> new JobRoleDetectionResult(null, 0.8, "依据"));
        for (double value : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new JobRoleDetectionResult(JobRole.JAVA, value, "依据"));
        }
        for (String reason : new String[]{null, "", "  "}) {
            assertThrows(IllegalArgumentException.class, () -> new JobRoleDetectionResult(JobRole.JAVA, 0.8, reason));
        }
    }

    @Test
    void acceptsBoundaryValuesAndPreservesContent() {
        var result = new JobRoleDetectionResult(JobRole.UNKNOWN, 0, "未知方向");
        assertEquals(JobRole.UNKNOWN, result.role());
        assertEquals(0, result.confidence());
        assertEquals("未知方向", result.reason());
        assertEquals(1, new JobRoleDetectionResult(JobRole.JAVA, 1, "依据").confidence());
    }
}

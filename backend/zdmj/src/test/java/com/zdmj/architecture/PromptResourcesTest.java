package com.zdmj.architecture;

import com.zdmj.common.prompt.PromptLoader;
import com.zdmj.jobService.api.JobRole;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证业务提示词资源完整性，防止遗漏文件进入运行环境。 */
class PromptResourcesTest {
    private final PromptLoader loader = new PromptLoader(new DefaultResourceLoader());

    @Test
    void everySupportedRoleHasBusinessTemplates() {
        for (String directory : new String[]{"resume-analysis", "job-student-match"}) {
            for (JobRole role : JobRole.values()) {
                assertFalse(loader.load(directory + "/" + role.slug()).isBlank());
            }
        }
    }

    @Test
    void fixedTemplatesExistAndDetectorListsEveryRole() {
        for (String path : new String[]{"job-detect", "system", "resume-import-parse",
                "knowledgebase-rag-system", "knowledgebase-query-rewrite", "career-report/generate", "career-report/polish",
                "career-report/integrity-check"}) {
            assertFalse(loader.load(path).isBlank());
        }
        String detector = loader.load("job-detect");
        for (JobRole role : JobRole.values()) {
            assertTrue(detector.contains(role.slug()));
        }
    }
}

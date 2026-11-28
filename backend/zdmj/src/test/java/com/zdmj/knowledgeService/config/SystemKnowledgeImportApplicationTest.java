package com.zdmj.knowledgeService.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SystemKnowledgeImportApplicationTest {
    @Test
    void 仅接受两个固定且不重复的导入参数() {
        String run = "20261127T000000Z-012345abcdef", hash = "a".repeat(64);
        assertThat(SystemKnowledgeImportApplication.parseArguments(new String[]{"--run-id=" + run, "--manifest-hash=" + hash}).runId()).isEqualTo(run);
        for (String[] args : new String[][]{{}, {"--run-id=" + run}, {"--run-id=" + run, "--manifest-hash=" + hash, "--scope=SYSTEM"},
                {"--run-id=" + run, "--run-id=" + run, "--manifest-hash=" + hash}, {"--run-id", "--manifest-hash=" + hash},
                {"--run-id=../test", "--manifest-hash=" + hash}, {"--run-id=" + run, "--manifest-hash=invalid"},
                {"--run-id=" + run, "--manifest-hash=" + hash, "text"}}) {
            assertThatThrownBy(() -> SystemKnowledgeImportApplication.parseArguments(args)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}

package com.zdmj.matchService.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.zdmj.common.prompt.PromptLoader;
import com.zdmj.jobService.api.JobRole;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证所有方向的匹配提示词存在、内容完整且使用约定的占位符格式。
 */
class JobStudentMatchPromptsTest {

    @Test
    void allMatchPrompts_shouldFollowContentContract() {
        var loader = new PromptLoader(new DefaultResourceLoader());
        for (JobRole role : JobRole.values()) {
            String promptName = "job-student-match/" + role.slug();
            String content = loader.load(promptName);
            assertThat(content).as(promptName).isNotBlank().doesNotContain("${");
            assertThat(content).as(promptName + " 缺少 basic 评分档").contains("## basic（基础要求）");
            assertThat(content).as(promptName + " 缺少 professionalSkill 评分档").contains("## professionalSkill（职业技能）");
            assertThat(content).as(promptName + " 缺少 professionalQuality 评分档").contains("## professionalQuality（职业素养）");
            assertThat(content).as(promptName + " 缺少 developmentPotential 评分档").contains("## developmentPotential（发展潜力）");
            assertThat(content).as(promptName + " 缺少四维独立打分约束").contains("维度间打分相互独立");
            assertThat(content).as(promptName + " 缺少 basic.gap 输出约束").contains("`basic.gap`");
            assertThat(content).as(promptName + " 缺少 professionalQuality.gap 输出约束").contains("`professionalQuality.gap`");
        }
    }

}

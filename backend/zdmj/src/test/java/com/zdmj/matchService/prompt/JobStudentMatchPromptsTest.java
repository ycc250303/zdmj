package com.zdmj.matchService.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StreamUtils;

import com.zdmj.common.ai.JobRole;
import com.zdmj.common.ai.PromptScenario;
import com.zdmj.common.ai.PromptUtil;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人岗匹配提示词回归测试。
 *
 * <p>背景：本仓库使用 Spring AI v1.x 的 {@code PromptTemplate}，其底层为 StringTemplate
 * （{@code StTemplateRenderer}），把 <code>{xxx}</code> 视为变量占位符。我们的
 * {@code job-student-match/*.md} 提示词正文里嵌入了 JSON 示例（含大量 <code>{</code>
 * <code>}</code>），一旦走 PromptTemplate 渲染（即传入非空 promptVars），就会触发
 * {@code STException("... came as a complete surprise to me")} 并被
 * {@code JobStudentMatchServiceImpl#generate} 的兜底 catch 吞成 11001 错误码。</p>
 *
 * <p>此测试用例（持久回归）确保：</p>
 * <ol>
 *   <li>所有 job-student-match 提示词都不再包含 <code>${...}</code> 字面占位符
 *       （这种写法 Spring AI 不识别，且会以原文 leak 给 LLM）；</li>
 *   <li>提示词全部能被 {@link PromptUtil#load(String)} 正常读取；</li>
 *   <li>chatOnce + null promptVars 约定由 {@code JobStudentMatchServiceImplTest} 覆盖
 *       （不在此重复跑真实 ChatUtil/UserLlmRouter 链路，避免 CI 环境差异）。</li>
 * </ol>
 */
class JobStudentMatchPromptsTest {

    private static List<String> matchPromptNames() {
        List<String> names = new ArrayList<>();
        PromptUtil promptUtil = new PromptUtil(new DefaultResourceLoader());
        for (JobRole role : JobRole.values()) {
            names.add(promptUtil.resolve(PromptScenario.JOB_STUDENT_MATCH, role));
        }
        return names.stream().distinct().toList();
    }

    @Test
    void allMatchPrompts_shouldNotContainDollarBracePlaceholders() throws IOException {
        for (String promptName : matchPromptNames()) {
            String content = loadRaw(promptName);
            assertFalse(content.contains("${"),
                    promptName + " 仍包含 ${...} 字面占位符；Spring AI 的 PromptTemplate"
                            + "（StringTemplate）只识别 {var} 写法，${var} 会被原文 leak 给 LLM。"
                            + "请改用上文/用户消息内联的自然语言指引。");
        }
    }

    @Test
    void allMatchPrompts_shouldBeLoadable_viaPromptUtil() {
        PromptUtil promptUtil = new PromptUtil(new DefaultResourceLoader());
        assertAll(matchPromptNames().stream().map(name -> () -> {
            String content = promptUtil.load(name);
            assertTrue(content != null && !content.isBlank(),
                    name + " 加载失败或内容为空");
        }));
    }

    @Test
    void allMatchPrompts_shouldHavePerDimensionEvaluationStandards() throws IOException {
        for (String promptName : matchPromptNames()) {
            String content = loadRaw(promptName);
            assertTrue(content.contains("## basic（基础要求）"),
                    promptName + " 缺少 basic 评分档");
            assertTrue(content.contains("## professionalSkill（职业技能）"),
                    promptName + " 缺少 professionalSkill 评分档");
            assertTrue(content.contains("## professionalQuality（职业素养）"),
                    promptName + " 缺少 professionalQuality 评分档");
            assertTrue(content.contains("## developmentPotential（发展潜力）"),
                    promptName + " 缺少 developmentPotential 评分档");
            assertTrue(content.contains("维度间打分相互独立"),
                    promptName + " 缺少四维独立打分约束");
            assertTrue(content.contains("`basic.gap`"),
                    promptName + " 缺少 basic.gap 输出约束");
            assertTrue(content.contains("`professionalQuality.gap`"),
                    promptName + " 缺少 professionalQuality.gap 输出约束");
        }
    }

    private static String loadRaw(String promptName) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource("prompts/" + promptName + ".md").getInputStream(),
                StandardCharsets.UTF_8);
    }
}

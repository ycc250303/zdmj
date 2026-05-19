package com.zdmj.matchService.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StreamUtils;

import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.PromptUtil;
import com.zdmj.common.util.prompt.PromptNames;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 人岗匹配提示词回归测试。
 *
 * <p>背景：本仓库使用 Spring AI v1.x 的 {@code PromptTemplate}，其底层为 StringTemplate
 * （{@code StTemplateRenderer}），把 <code>{xxx}</code> 视为变量占位符。我们的
 * {@code job-student-match/*.md} 提示词正文里嵌入了 JSON 示例（含大量 <code>{</code>
 * <code>}</code>），一旦走 PromptTemplate 渲染（即传入非空 promptVars），就会触发
 * {@code STException("... came as a complete surprise to me")} 并被
 * {@code JobStudentMatchServiceImpl#generate} 的兜底 catch 吞成 8301 错误码。</p>
 *
 * <p>此测试用例（持久回归）确保：</p>
 * <ol>
 *   <li>所有 job-student-match 提示词都不再包含 <code>${...}</code> 字面占位符
 *       （这种写法 Spring AI 不识别，且会以原文 leak 给 LLM）；</li>
 *   <li>提示词全部能被 {@link PromptUtil#load(String)} 正常读取；</li>
 *   <li>对所有提示词调用 {@link ChatUtil#chatOnce(String, String, java.util.Map)}
 *       并传 {@code null} promptVars，不会触发 PromptTemplate 渲染，因而不会再现
 *       「换岗位仍然 8301」的故障。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class JobStudentMatchPromptsTest {

    private static final List<String> PROMPT_NAMES = List.of(
            PromptNames.JOB_STUDENT_MATCH_DEFAULT,
            PromptNames.JOB_STUDENT_MATCH_JAVA_BACKEND,
            PromptNames.JOB_STUDENT_MATCH_FRONTEND,
            PromptNames.JOB_STUDENT_MATCH_ALGORITHM,
            PromptNames.JOB_STUDENT_MATCH_AI_AGENT,
            PromptNames.JOB_STUDENT_MATCH_CPP,
            PromptNames.JOB_STUDENT_MATCH_SOFTWARE_TEST,
            PromptNames.JOB_STUDENT_MATCH_DATA_ANALYST,
            PromptNames.JOB_STUDENT_MATCH_BIG_DATA,
            PromptNames.JOB_STUDENT_MATCH_DEVOPS_SRE,
            PromptNames.JOB_STUDENT_MATCH_CYBERSECURITY);

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient statelessChatClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClientWithMemory;

    @Test
    void allMatchPrompts_shouldNotContainDollarBracePlaceholders() throws IOException {
        for (String promptName : PROMPT_NAMES) {
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
        assertAll(PROMPT_NAMES.stream().map(name -> () -> {
            String content = promptUtil.load(name);
            assertTrue(content != null && !content.isBlank(),
                    name + " 加载失败或内容为空");
        }));
    }

    /**
     * 核心回归：模拟「换任意岗位都报 8301」的真实链路 —— 用真实的
     * {@link PromptUtil} 从 classpath 装载我们 5 份提示词，再让真实
     * {@link ChatUtil#chatOnce} 跑一遍，要求 **不能抛任何模板渲染异常**。
     *
     * <p>若有人未来重新引入非空 promptVars 或 ${var} 占位符，这条用例会直接红。</p>
     */
    @Test
    void chatOnce_withRealPromptUtil_andNullPromptVars_shouldNotThrowSTException() {
        PromptUtil promptUtil = new PromptUtil(new DefaultResourceLoader());
        ChatUtil chatUtil = new ChatUtil(statelessChatClient, chatClientWithMemory, promptUtil);
        when(statelessChatClient.prompt()
                .system(org.mockito.ArgumentMatchers.anyString())
                .user(org.mockito.ArgumentMatchers.anyString())
                .call()
                .content()).thenReturn("ok");

        for (String promptName : PROMPT_NAMES) {
            assertDoesNotThrow(
                    () -> chatUtil.chatOnce("dummy user message", promptName, null),
                    "对 " + promptName + " 调用 chatOnce(..., null) 不应抛任何异常；"
                            + "若为 IllegalArgumentException(\"The template string is not valid.\")，"
                            + "说明上游又改回了走 PromptTemplate 渲染的路径");
        }
    }

    private static String loadRaw(String promptName) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource("prompts/" + promptName + ".md").getInputStream(),
                StandardCharsets.UTF_8);
    }
}

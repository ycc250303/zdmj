package com.zdmj.common.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 岗位方向的单一事实源：规范身份（slug）、输入别名、关键词直出词表。
 *
 * <p>落库 {@code targetRoleType} 与提示词路径末段均使用 {@link #slug()}（hyphen）。
 * {@link #fromString(String)} 兼容 LLM 的 underscore 写法与简短别名。</p>
 */
public enum JobRole {

    JAVA("java-backend",
            List.of("java", "backend"),
            List.of("java", "spring", "spring boot", "mybatis", "mysql", "redis", "jvm")),
    FRONTEND("frontend",
            List.of("fe", "web_frontend", "web-frontend"),
            List.of("react", "vue", "typescript", "javascript", "webpack", "vite", "css", "html")),
    CPP("cpp",
            List.of("c++", "c_cpp", "c-cpp"),
            List.of("c++", "cpp", "stl", "cmake", "gdb", "linux", "多线程", "内存")),
    SOFTWARE_TEST("software-test",
            List.of("test", "qa"),
            List.of("测试", "test case", "pytest", "selenium", "jmeter", "postman", "缺陷")),
    AI_AGENT("ai-agent",
            List.of("agent", "ai"),
            List.of("llm", "大模型", "agent", "rag", "langchain", "prompt", "embedding")),
    ALGORITHM("algorithm",
            List.of("algo", "ml", "machine_learning", "machine-learning"),
            List.of("算法", "machine learning", "深度学习", "pytorch", "tensorflow")),
    DATA_ANALYST("data-analyst",
            List.of("analyst", "da"),
            List.of("数据分析", "sql", "tableau", "powerbi", "excel", "指标")),
    BIG_DATA("big-data",
            List.of("data_engineer", "data-engineer", "de"),
            List.of("hadoop", "spark", "flink", "hive", "数仓", "kafka")),
    DEVOPS_SRE("devops-sre",
            List.of("devops", "sre"),
            List.of("devops", "sre", "k8s", "kubernetes", "docker", "ci/cd", "ansible")),
    CYBERSECURITY("cybersecurity",
            List.of("security", "sec", "network_security", "network-security"),
            List.of("安全", "渗透", "漏洞", "owasp", "攻防", "合规")),
    UNKNOWN("default",
            List.of("unknown"),
            List.of());

    private static final Map<String, JobRole> BY_ALIAS = buildAliasIndex();

    private final String slug;
    private final List<String> aliases;
    private final List<String> keywords;

    JobRole(String slug, List<String> extraAliases, List<String> keywords) {
        this.slug = slug;
        List<String> all = new ArrayList<>();
        all.add(slug);
        all.add(slug.replace('-', '_'));
        all.addAll(extraAliases);
        this.aliases = List.copyOf(all.stream().distinct().toList());
        this.keywords = List.copyOf(keywords);
    }

    public String slug() {
        return slug;
    }

    public List<String> keywords() {
        return keywords;
    }

    /**
     * 将 LLM / 库内 / 前端传入的 roleCode 归一化为枚举。无法识别时返回 {@link #UNKNOWN}。
     */
    public static JobRole fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return BY_ALIAS.getOrDefault(raw.trim().toLowerCase(Locale.ROOT), UNKNOWN);
    }

    /**
     * 从提示词路径末段还原角色，例如 {@code job-requirement/java-backend} → {@link #JAVA}。
     */
    public static JobRole fromPromptName(String promptName) {
        if (promptName == null || promptName.isBlank()) {
            return UNKNOWN;
        }
        int idx = promptName.lastIndexOf('/');
        return fromString(idx >= 0 ? promptName.substring(idx + 1) : promptName);
    }

    private static Map<String, JobRole> buildAliasIndex() {
        Map<String, JobRole> index = new LinkedHashMap<>();
        for (JobRole role : values()) {
            for (String alias : role.aliases) {
                index.putIfAbsent(alias.toLowerCase(Locale.ROOT), role);
            }
        }
        return Map.copyOf(index);
    }
}

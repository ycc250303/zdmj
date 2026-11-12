package com.zdmj.jobService.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 岗位方向的单一事实源：规范身份（slug）、关键词直出词表。
 *
 * <p>落库 {@code targetRoleType} 与提示词路径末段均使用 {@link #slug()}（hyphen）。
 * {@link #fromString(String)} 兼容 LLM 的 underscore 写法。</p>
 *
 * <p>参与关键词计分的方向词表等长：语言、框架（可并列同类替代，如 pytest/junit）、组件、工具。
 * 不含缩写/中英/上下位重复（如 k8s 与 kubernetes）；跨方向可共享语言（如 python）。</p>
 */
public enum JobRole {

    /** Java 后端，存储代码为 java-backend。 */
    JAVA("java-backend",
            List.of("java", "spring boot", "mybatis", "mysql", "redis", "postgresql", "maven", "jvm")),
    /** 前端，存储代码为 frontend。 */
    FRONTEND("frontend",
            List.of("javascript", "typescript", "react", "vue", "css", "webpack", "vite", "npm")),
    /** C++ 开发，存储代码为 cpp。 */
    CPP("cpp",
            List.of("c++", "stl", "qt", "boost", "cmake", "gdb", "linux", "多线程")),
    /** 软件测试，存储代码为 software-test。 */
    SOFTWARE_TEST("software-test",
            List.of("测试", "pytest", "junit", "selenium", "jmeter", "postman", "cypress", "缺陷")),
    /** AI Agent 开发，存储代码为 ai-agent。 */
    AI_AGENT("ai-agent",
            List.of("python", "llm", "langchain", "llamaindex", "rag", "embedding", "milvus", "agent")),
    /** 算法，存储代码为 algorithm。 */
    ALGORITHM("algorithm",
            List.of("python", "算法", "pytorch", "tensorflow", "机器学习", "深度学习", "sklearn", "nlp")),
    /** 数据分析，存储代码为 data-analyst。 */
    DATA_ANALYST("data-analyst",
            List.of("python", "sql", "pandas", "excel", "tableau", "powerbi", "数据分析", "指标")),
    /** 大数据，存储代码为 big-data。 */
    BIG_DATA("big-data",
            List.of("scala", "spark", "flink", "hadoop", "hive", "kafka", "数仓", "hbase")),
    /** 运维与可靠性工程，存储代码为 devops-sre。 */
    DEVOPS_SRE("devops-sre",
            List.of("golang", "linux", "kubernetes", "docker", "terraform", "ansible", "prometheus", "ci/cd")),
    /** 网络安全，存储代码为 cybersecurity。 */
    CYBERSECURITY("cybersecurity",
            List.of("python", "渗透", "漏洞", "owasp", "burpsuite", "wireshark", "攻防", "合规")),
    /** 未知方向，存储代码及模板名称为 default。 */
    UNKNOWN("default",
            List.of());

    private static final Map<String, JobRole> BY_CODE = buildCodeIndex();

    /** 对应 target_role_type 列及提示词路径末段的规范代码。 */
    private final String slug;
    /** 关键词评分使用的不可变词表，未知方向为空。 */
    private final List<String> keywords;

    JobRole(String slug, List<String> keywords) {
        this.slug = slug;
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
        return BY_CODE.getOrDefault(raw.trim().toLowerCase(Locale.ROOT), UNKNOWN);
    }

    /**
     * 从提示词路径末段还原角色，例如 {@code resume-analysis/java-backend} → {@link #JAVA}。
     */
    public static JobRole fromPromptName(String promptName) {
        if (promptName == null || promptName.isBlank()) {
            return UNKNOWN;
        }
        int idx = promptName.lastIndexOf('/');
        return fromString(idx >= 0 ? promptName.substring(idx + 1) : promptName);
    }

    private static Map<String, JobRole> buildCodeIndex() {
        Map<String, JobRole> index = new LinkedHashMap<>();
        for (JobRole role : values()) {
            index.putIfAbsent(role.slug.toLowerCase(Locale.ROOT), role);
            index.putIfAbsent(role.slug.replace('-', '_').toLowerCase(Locale.ROOT), role);
        }
        return Map.copyOf(index);
    }
}

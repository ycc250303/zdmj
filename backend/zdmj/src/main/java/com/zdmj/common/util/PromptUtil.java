package com.zdmj.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class PromptUtil {

    private static final String PREFIX = "classpath:prompts/";
    private static final String SUFFIX = ".md";
    private final ResourceLoader resourceLoader;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public PromptUtil(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载提示词
     * 
     * @return 提示词
     * @param fileName 文件名，如 "system.md"
     */
    public String load(String fileName) {
        return cache.computeIfAbsent(fileName, this::loadUncached);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * 加载提示词
     * 
     * @param fileName 文件名，如 "system.md"
     * @return 提示词
     */
    private String loadUncached(String fileName) {
        String location = PREFIX + fileName + SUFFIX;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Prompt not found: " + location);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt: " + location, e);
        }
    }

    public final class PromptNames {
        private PromptNames() {
        }

        /** 通用对话 system */
        public static final String SYSTEM = "system";
        /** 生成会话标题 */
        public static final String GENERATE_CONVERSATION_TITLE = "generate-conversation-title";

        /** 岗位要求画像 Java 后端 */
        public static final String JOB_REQUIREMENT_JAVA_BACKEND = "job-requirement/java-backend";
        /** 岗位要求画像 前端 */
        public static final String JOB_REQUIREMENT_FRONTEND = "job-requirement/frontend";
        /** 岗位要求画像 C/C++ */
        public static final String JOB_REQUIREMENT_CPP = "job-requirement/cpp";
        /** 岗位要求画像 软件测试 */
        public static final String JOB_REQUIREMENT_SOFTWARE_TEST = "job-requirement/software-test";
        /** 岗位要求画像 AI/Agent 开发 */
        public static final String JOB_REQUIREMENT_AI_AGENT = "job-requirement/ai-agent";
        /** 岗位要求画像 算法 */
        public static final String JOB_REQUIREMENT_ALGORITHM = "job-requirement/algorithm";
        /** 岗位要求画像 数据分析 */
        public static final String JOB_REQUIREMENT_DATA_ANALYST = "job-requirement/data-analyst";
        /** 岗位要求画像 大数据 */
        public static final String JOB_REQUIREMENT_BIG_DATA = "job-requirement/big-data";
        /** 岗位要求画像 DevOps/SRE */
        public static final String JOB_REQUIREMENT_DEVOPS_SRE = "job-requirement/devops-sre";
        /** 岗位要求画像 网络安全 */
        public static final String JOB_REQUIREMENT_CYBERSECURITY = "job-requirement/cybersecurity";
        /** 岗位要求画像 默认兜底 */
        public static final String JOB_REQUIREMENT_DEFAULT = "job-requirement/default";
        /** 知识库 RAG 问答 */
        public static final String KNOWLEDGEBASE_RAG_SYSTEM = "knowledgebase-rag-system";
        /** 知识库查询改写 */
        public static final String KNOWLEDGEBASE_RAG_QUERY_REWRITE = "knowledgebase-query-rewrite";
        /** 简历分析 Java 后端 */
        public static final String RESUME_ANALYSIS_JAVA_BACKEND = "resume-analysis/java-backend";
        /** 简历分析 前端 */
        public static final String RESUME_ANALYSIS_FRONTEND = "resume-analysis/frontend";
        /** 简历分析 C/C++ */
        public static final String RESUME_ANALYSIS_CPP = "resume-analysis/cpp";
        /** 简历分析 软件测试 */
        public static final String RESUME_ANALYSIS_SOFTWARE_TEST = "resume-analysis/software-test";
        /** 简历分析 AI/Agent 开发 */
        public static final String RESUME_ANALYSIS_AI_AGENT = "resume-analysis/ai-agent";
        /** 简历分析 算法 */
        public static final String RESUME_ANALYSIS_ALGORITHM = "resume-analysis/algorithm";
        /** 简历分析 数据分析 */
        public static final String RESUME_ANALYSIS_DATA_ANALYST = "resume-analysis/data-analyst";
        /** 简历分析 默认兜底 */
        public static final String RESUME_ANALYSIS_DEFAULT = "resume-analysis/default";
        /** 简历分析 大数据 */
        public static final String RESUME_ANALYSIS_BIG_DATA = "resume-analysis/big-data";
        /** 简历分析 DevOps/SRE */
        public static final String RESUME_ANALYSIS_DEVOPS_SRE = "resume-analysis/devops-sre";
        /** 简历分析 网络安全 */
        public static final String RESUME_ANALYSIS_CYBERSECURITY = "resume-analysis/cybersecurity";
        /** 岗位分类 job-detect */
        public static final String JOB_DETECT = "job-detect";

        /** 岗位关联图谱 Java 后端 */
        public static final String JOB_CAREER_GRAPH_JAVA_BACKEND = "job-career-graph/java-backend";
        /** 岗位关联图谱 前端 */
        public static final String JOB_CAREER_GRAPH_FRONTEND = "job-career-graph/frontend";
        /** 岗位关联图谱 AI/Agent 开发 */
        public static final String JOB_CAREER_GRAPH_AI_AGENT = "job-career-graph/ai-agent";
        /** 岗位关联图谱 默认兜底 */
        public static final String JOB_CAREER_GRAPH_DEFAULT = "job-career-graph/default";
    }

    public enum JobRole {
        JAVA,
        FRONTEND,
        CPP,
        SOFTWARE_TEST,
        AI_AGENT,
        ALGORITHM,
        DATA_ANALYST,
        BIG_DATA,
        DEVOPS_SRE,
        CYBERSECURITY,
        UNKNOWN
    }

    /**
     * 将 LLM/配置中的 roleCode 归一化为 JobRole（兼容 java_backend、backend、大小写等）
     */
    public static JobRole getJobRoleByString(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return JobRole.UNKNOWN;
        }
        String key = roleCode.trim().toLowerCase();
        return switch (key) {
            case "java", "java_backend", "backend" -> JobRole.JAVA;
            case "frontend", "fe", "web_frontend" -> JobRole.FRONTEND;
            case "cpp", "c++", "c_cpp" -> JobRole.CPP;
            case "software_test", "test", "qa" -> JobRole.SOFTWARE_TEST;
            case "ai_agent", "ai-agent", "agent", "ai" -> JobRole.AI_AGENT;
            case "algorithm", "algo", "ml", "machine_learning" -> JobRole.ALGORITHM;
            case "data_analyst", "data-analyst", "analyst", "da" -> JobRole.DATA_ANALYST;
            case "big_data", "big-data", "data_engineer", "de" -> JobRole.BIG_DATA;
            case "devops_sre", "devops-sre", "devops", "sre" -> JobRole.DEVOPS_SRE;
            case "cybersecurity", "security", "sec", "network_security" -> JobRole.CYBERSECURITY;
            case "unknown", "default" -> JobRole.UNKNOWN;
            default -> JobRole.UNKNOWN;
        };
    }

    /**
     * 岗位角色 -> PromptName 映射
     */
    public static String getResumeAnalysisPromptName(JobRole role) {
        if (role == null) {
            return PromptNames.RESUME_ANALYSIS_DEFAULT;
        }
        return switch (role) {
            case JAVA -> PromptNames.RESUME_ANALYSIS_JAVA_BACKEND;
            case FRONTEND -> PromptNames.RESUME_ANALYSIS_FRONTEND;
            case CPP -> PromptNames.RESUME_ANALYSIS_CPP;
            case SOFTWARE_TEST -> PromptNames.RESUME_ANALYSIS_SOFTWARE_TEST;
            case AI_AGENT -> PromptNames.RESUME_ANALYSIS_AI_AGENT;
            case ALGORITHM -> PromptNames.RESUME_ANALYSIS_ALGORITHM;
            case DATA_ANALYST -> PromptNames.RESUME_ANALYSIS_DATA_ANALYST;
            case BIG_DATA -> PromptNames.RESUME_ANALYSIS_BIG_DATA;
            case DEVOPS_SRE -> PromptNames.RESUME_ANALYSIS_DEVOPS_SRE;
            case CYBERSECURITY -> PromptNames.RESUME_ANALYSIS_CYBERSECURITY;
            case UNKNOWN -> PromptNames.RESUME_ANALYSIS_DEFAULT;
        };
    }

    /**
     * 岗位角色 -> 岗位要求画像 PromptName 映射
     */
    public static String getJobRequirementPromptName(JobRole role) {
        if (role == null) {
            return PromptNames.JOB_REQUIREMENT_DEFAULT;
        }
        return switch (role) {
            case JAVA -> PromptNames.JOB_REQUIREMENT_JAVA_BACKEND;
            case FRONTEND -> PromptNames.JOB_REQUIREMENT_FRONTEND;
            case CPP -> PromptNames.JOB_REQUIREMENT_CPP;
            case SOFTWARE_TEST -> PromptNames.JOB_REQUIREMENT_SOFTWARE_TEST;
            case AI_AGENT -> PromptNames.JOB_REQUIREMENT_AI_AGENT;
            case ALGORITHM -> PromptNames.JOB_REQUIREMENT_ALGORITHM;
            case DATA_ANALYST -> PromptNames.JOB_REQUIREMENT_DATA_ANALYST;
            case BIG_DATA -> PromptNames.JOB_REQUIREMENT_BIG_DATA;
            case DEVOPS_SRE -> PromptNames.JOB_REQUIREMENT_DEVOPS_SRE;
            case CYBERSECURITY -> PromptNames.JOB_REQUIREMENT_CYBERSECURITY;
            case UNKNOWN -> PromptNames.JOB_REQUIREMENT_DEFAULT;
        };
    }

    /**
     * 岗位角色 -> 规范 roleCode 映射（用于落库）
     */
    public static String getRoleCodeByJobRole(JobRole role) {
        if (role == null) {
            return "default";
        }
        return switch (role) {
            case JAVA -> "java_backend";
            case FRONTEND -> "frontend";
            case CPP -> "cpp";
            case SOFTWARE_TEST -> "software_test";
            case AI_AGENT -> "ai_agent";
            case ALGORITHM -> "algorithm";
            case DATA_ANALYST -> "data_analyst";
            case BIG_DATA -> "big_data";
            case DEVOPS_SRE -> "devops_sre";
            case CYBERSECURITY -> "cybersecurity";
            case UNKNOWN -> "default";
        };
    }

    /**
     * 岗位角色 -> 岗位关联图谱 PromptName 映射。
     *
     * <p>仅为核心方向（Java 后端 / 前端 / AI）提供专属图谱提示词，其它角色统一走
     * {@link PromptNames#JOB_CAREER_GRAPH_DEFAULT}，以控制提示词数量同时保证主流岗位输出质量。</p>
     */
    public static String getJobCareerGraphPromptName(JobRole role) {
        if (role == null) {
            return PromptNames.JOB_CAREER_GRAPH_DEFAULT;
        }
        return switch (role) {
            case JAVA -> PromptNames.JOB_CAREER_GRAPH_JAVA_BACKEND;
            case FRONTEND -> PromptNames.JOB_CAREER_GRAPH_FRONTEND;
            case AI_AGENT -> PromptNames.JOB_CAREER_GRAPH_AI_AGENT;
            default -> PromptNames.JOB_CAREER_GRAPH_DEFAULT;
        };
    }

    /**
     * 提示词名称转展示类型（仅保留最后一段，如 job-requirement/software-test -> software-test）
     */
    public static String getPromptDisplayType(String promptName) {
        if (promptName == null || promptName.isBlank()) {
            return promptName;
        }
        int idx = promptName.lastIndexOf('/');
        return idx >= 0 ? promptName.substring(idx + 1) : promptName;
    }
}

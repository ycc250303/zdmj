package com.zdmj.common.ai;

import com.zdmj.common.ai.prompt.PromptNames;

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
     * 将 LLM/配置中的 roleCode 归一化为 JobRole。
     *
     * <p>同时兼容三类输入：</p>
     * <ul>
     *   <li>LLM 返回的下划线写法：{@code java_backend / ai_agent / big_data}；</li>
     *   <li>前端/数据库存储的连字符写法（即提示词末段，由 {@link #getPromptDisplayType(String)} 输出）：
     *       {@code java-backend / ai-agent / big-data / software-test / devops-sre}；</li>
     *   <li>简短别名：{@code backend / fe / qa / ml / sre / da / de / sec ...}。</li>
     * </ul>
     */
    public static JobRole getJobRoleByString(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return JobRole.UNKNOWN;
        }
        String key = roleCode.trim().toLowerCase();
        return switch (key) {
            case "java", "java_backend", "java-backend", "backend" -> JobRole.JAVA;
            case "frontend", "fe", "web_frontend", "web-frontend" -> JobRole.FRONTEND;
            case "cpp", "c++", "c_cpp", "c-cpp" -> JobRole.CPP;
            case "software_test", "software-test", "test", "qa" -> JobRole.SOFTWARE_TEST;
            case "ai_agent", "ai-agent", "agent", "ai" -> JobRole.AI_AGENT;
            case "algorithm", "algo", "ml", "machine_learning", "machine-learning" -> JobRole.ALGORITHM;
            case "data_analyst", "data-analyst", "analyst", "da" -> JobRole.DATA_ANALYST;
            case "big_data", "big-data", "data_engineer", "data-engineer", "de" -> JobRole.BIG_DATA;
            case "devops_sre", "devops-sre", "devops", "sre" -> JobRole.DEVOPS_SRE;
            case "cybersecurity", "security", "sec", "network_security", "network-security" -> JobRole.CYBERSECURITY;
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
     * 岗位角色 -> 人岗匹配 PromptName 映射。
     *
     * <p>与 {@link #getResumeAnalysisPromptName(JobRole)} 的岗位划分对齐：各方向专属提示词；
     * 未知角色走 {@link PromptNames#JOB_STUDENT_MATCH_DEFAULT}。</p>
     */
    public static String getJobStudentMatchPromptName(JobRole role) {
        if (role == null) {
            return PromptNames.JOB_STUDENT_MATCH_DEFAULT;
        }
        return switch (role) {
            case JAVA -> PromptNames.JOB_STUDENT_MATCH_JAVA_BACKEND;
            case FRONTEND -> PromptNames.JOB_STUDENT_MATCH_FRONTEND;
            case ALGORITHM -> PromptNames.JOB_STUDENT_MATCH_ALGORITHM;
            case AI_AGENT -> PromptNames.JOB_STUDENT_MATCH_AI_AGENT;
            case CPP -> PromptNames.JOB_STUDENT_MATCH_CPP;
            case SOFTWARE_TEST -> PromptNames.JOB_STUDENT_MATCH_SOFTWARE_TEST;
            case DATA_ANALYST -> PromptNames.JOB_STUDENT_MATCH_DATA_ANALYST;
            case BIG_DATA -> PromptNames.JOB_STUDENT_MATCH_BIG_DATA;
            case DEVOPS_SRE -> PromptNames.JOB_STUDENT_MATCH_DEVOPS_SRE;
            case CYBERSECURITY -> PromptNames.JOB_STUDENT_MATCH_CYBERSECURITY;
            case UNKNOWN -> PromptNames.JOB_STUDENT_MATCH_DEFAULT;
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

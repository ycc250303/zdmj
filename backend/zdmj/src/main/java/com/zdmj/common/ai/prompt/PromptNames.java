package com.zdmj.common.ai.prompt;

/**
 * 各业务场景下 {@code classpath:prompts/{name}.md} 的 {@code name} 常量（不含 {@code .md} 后缀）。
 */
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

    /** 人岗匹配 Java 后端 */
    public static final String JOB_STUDENT_MATCH_JAVA_BACKEND = "job-student-match/java-backend";
    /** 人岗匹配 前端 */
    public static final String JOB_STUDENT_MATCH_FRONTEND = "job-student-match/frontend";
    /** 人岗匹配 算法 */
    public static final String JOB_STUDENT_MATCH_ALGORITHM = "job-student-match/algorithm";
    /** 人岗匹配 AI/Agent 开发 */
    public static final String JOB_STUDENT_MATCH_AI_AGENT = "job-student-match/ai-agent";
    /** 人岗匹配 C/C++ */
    public static final String JOB_STUDENT_MATCH_CPP = "job-student-match/cpp";
    /** 人岗匹配 软件测试 */
    public static final String JOB_STUDENT_MATCH_SOFTWARE_TEST = "job-student-match/software-test";
    /** 人岗匹配 数据分析 */
    public static final String JOB_STUDENT_MATCH_DATA_ANALYST = "job-student-match/data-analyst";
    /** 人岗匹配 大数据 */
    public static final String JOB_STUDENT_MATCH_BIG_DATA = "job-student-match/big-data";
    /** 人岗匹配 DevOps/SRE */
    public static final String JOB_STUDENT_MATCH_DEVOPS_SRE = "job-student-match/devops-sre";
    /** 人岗匹配 网络安全 */
    public static final String JOB_STUDENT_MATCH_CYBERSECURITY = "job-student-match/cybersecurity";
    /** 人岗匹配 默认兜底 */
    public static final String JOB_STUDENT_MATCH_DEFAULT = "job-student-match/default";

    /** 职业发展报告 生成 */
    public static final String CAREER_REPORT_GENERATE = "career-report/generate";
    /** 职业发展报告 润色 */
    public static final String CAREER_REPORT_POLISH = "career-report/polish";
    /** 职业发展报告 完整性检查 */
    public static final String CAREER_REPORT_INTEGRITY_CHECK = "career-report/integrity-check";
}

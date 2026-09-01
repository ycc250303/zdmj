package com.zdmj.common.ai.prompt;

/**
 * 与岗位方向无关的 {@code classpath:prompts/{name}.md} 常量（不含 {@code .md} 后缀）。
 *
 * <p>按岗位拆分的提示词经 {@code PromptUtil.resolve(PromptScenario, JobRole)} 约定路由，不在此枚举。</p>
 */
public final class PromptNames {

    private PromptNames() {
    }

    /** 通用对话 system */
    public static final String SYSTEM = "system";
    /** 生成会话标题 */
    public static final String GENERATE_CONVERSATION_TITLE = "generate-conversation-title";
    /** 知识库 RAG 问答 */
    public static final String KNOWLEDGEBASE_RAG_SYSTEM = "knowledgebase-rag-system";
    /** 知识库查询改写 */
    public static final String KNOWLEDGEBASE_RAG_QUERY_REWRITE = "knowledgebase-query-rewrite";
    /** 岗位分类 job-detect */
    public static final String JOB_DETECT = "job-detect";
    /** 简历导入：从纯文本结构化提取字段 */
    public static final String RESUME_IMPORT_PARSE = "resume-import-parse";
    /** 简历导入：根据候选句判断奖项 */
    public static final String RESUME_IMPORT_AWARDS = "resume-import-awards";
    /** 职业发展报告 生成 */
    public static final String CAREER_REPORT_GENERATE = "career-report/generate";
    /** 职业发展报告 润色 */
    public static final String CAREER_REPORT_POLISH = "career-report/polish";
    /** 职业发展报告 完整性检查 */
    public static final String CAREER_REPORT_INTEGRITY_CHECK = "career-report/integrity-check";
}

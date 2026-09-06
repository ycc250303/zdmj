package com.zdmj.common.async;

/**
 * 异步任务类型，与表 {@code async_llm_tasks.task_type}（CHECK 1..10）一致。
 * 全部按发起人隔离，无全站共享任务。
 *
 * <p>{@link #KB_EMBED}/{@link #KB_DELETE} 为预留迁入位；向量化第一期仍可走
 * {@code knowledge_vector_tasks}。</p>
 */
public enum AsyncTaskType {

    STUDENT_PROFILE(1, "学生能力画像", StreamKind.LLM),
    JOB_PROFILE(2, "岗位能力画像", StreamKind.LLM),
    JOB_GRAPH(3, "岗位职业图谱", StreamKind.LLM),
    JOB_MATCH(4, "人岗匹配", StreamKind.LLM),
    CAREER_REPORT(5, "职业发展报告", StreamKind.LLM),
    REPORT_POLISH(6, "报告润色", StreamKind.LLM),
    REPORT_CHECK(7, "报告完整性检查", StreamKind.LLM),
    RESUME_PARSE(8, "简历识别", StreamKind.LLM),
    /** 预留迁入位；一期仍走 {@code knowledge_vector_tasks} */
    KB_EMBED(9, "知识库向量化", StreamKind.EMBED),
    /** 预留迁入位；一期仍走 {@code knowledge_vector_tasks} */
    KB_DELETE(10, "知识库向量删除", StreamKind.EMBED);

    /**
     * 两条 Redis Stream：LLM 与向量化限流不同。
     */
    public enum StreamKind {
        /** {@code zdmj:llm:stream}，画像 / 匹配 / 报告 / 简历识别 */
        LLM,
        /** {@code zdmj:embed:stream}，知识库向量化与删除（四期） */
        EMBED
    }

    /** 库内整型码，写入 {@code async_llm_tasks.task_type} */
    private final int code;
    /** 中文展示名 */
    private final String label;
    /** 决定走 LLM 流还是 Embedding 流 */
    private final StreamKind streamKind;

    AsyncTaskType(int code, String label, StreamKind streamKind) {
        this.code = code;
        this.label = label;
        this.streamKind = streamKind;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public StreamKind getStreamKind() {
        return streamKind;
    }

    /**
     * 按库内整型码解析；未知或空返回 {@code null}。
     */
    public static AsyncTaskType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AsyncTaskType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}

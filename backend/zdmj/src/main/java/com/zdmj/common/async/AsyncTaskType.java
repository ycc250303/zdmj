package com.zdmj.common.async;

/**
 * 异步任务类型，与表 {@code async_llm_tasks.task_type}（CHECK 1..10）一致。
 *
 * <p>{@link #KB_EMBED}/{@link #KB_DELETE} 为预留迁入位；向量化第一期仍可走
 * {@code knowledge_vector_tasks}。</p>
 */
public enum AsyncTaskType {

    STUDENT_PROFILE(1, "学生能力画像", StreamKind.LLM, false),
    JOB_PROFILE(2, "岗位能力画像", StreamKind.LLM, true),
    JOB_GRAPH(3, "岗位职业图谱", StreamKind.LLM, true),
    JOB_MATCH(4, "人岗匹配", StreamKind.LLM, false),
    CAREER_REPORT(5, "职业发展报告", StreamKind.LLM, false),
    REPORT_POLISH(6, "报告润色", StreamKind.LLM, false),
    REPORT_CHECK(7, "报告完整性检查", StreamKind.LLM, false),
    RESUME_PARSE(8, "简历识别", StreamKind.LLM, false),
    KB_EMBED(9, "知识库向量化", StreamKind.EMBED, false),
    KB_DELETE(10, "知识库向量删除", StreamKind.EMBED, false);

    /**
     * 两条 Redis Stream：LLM 与向量化限流不同。
     */
    public enum StreamKind {
        LLM,
        EMBED
    }

    private final int code;
    private final String label;
    private final StreamKind streamKind;
    private final boolean shared;

    AsyncTaskType(int code, String label, StreamKind streamKind, boolean shared) {
        this.code = code;
        this.label = label;
        this.streamKind = streamKind;
        this.shared = shared;
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
     * 岗位画像/图谱为全站共享：甲在生成时乙点击拿到同一任务。
     */
    public boolean isShared() {
        return shared;
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

package com.zdmj.common.async;

/**
 * 异步任务状态，与表 {@code async_llm_tasks.status}、{@code knowledge_vector_tasks.status} 一致。
 */
public enum AsyncTaskStatus {

    /** 已入队，等待消费者抢占 */
    PENDING(1, "排队中"),
    /** 已被消费者 claim，正在执行 */
    RUNNING(2, "执行中"),
    /** 业务结果已落库（或写入任务 result） */
    SUCCESS(3, "成功"),
    /** 超过重试或不可恢复失败 */
    FAILED(4, "失败");

    private final int code;
    private final String label;

    AsyncTaskStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 进行中：占部分唯一索引 {@code uk_async_llm_tasks_inflight}。
     */
    public boolean isInFlight() {
        return this == PENDING || this == RUNNING;
    }

    /**
     * 终态：SUCCESS/FAILED 不占唯一索引，用户可再点生成新任务。
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    /**
     * 按库内整型码解析；未知或空返回 {@code null}。
     */
    public static AsyncTaskStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AsyncTaskStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}

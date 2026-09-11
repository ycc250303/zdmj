package com.zdmj.common.async;

/**
 * {@code async_llm_tasks.biz_key} 拼装。与部分唯一索引 {@code uk_async_llm_tasks_inflight} 对齐。
 */
public final class AsyncBizKeys {

    private AsyncBizKeys() {
    }

    /** 按用户去重，如学生画像、简历识别。 */
    public static String user(long userId) {
        return "user:" + userId;
    }

    /** 按用户+岗位去重，如岗位画像、人岗匹配、报告生成。 */
    public static String userJob(long userId, long jobId) {
        return "user:" + userId + ":job:" + jobId;
    }

    /** 按报告去重，如润色、完整性检查。 */
    public static String report(long reportId) {
        return "report:" + reportId;
    }
}

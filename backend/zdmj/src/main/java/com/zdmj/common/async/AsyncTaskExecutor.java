package com.zdmj.common.async;

/**
 * 按 {@link AsyncTaskType} 执行已 claim 的异步任务。
 *
 * <p>实现类调用现有域 Service（generate/polish/check/parse），禁止再 {@code enqueue}。
 * 登录态由消费者写入 {@code UserHolder}。</p>
 */
public interface AsyncTaskExecutor {

    /** 本执行器承接的任务类型。 */
    AsyncTaskType type();

    /**
     * 同步执行业务并落库。
     *
     * @param task 已 claim 为 RUNNING 的行，payload 为入队 JSON
     * @return 写入 {@code async_llm_tasks.result} 的 JSON；有独立业务表时返回 {@code null}
     */
    String execute(AsyncLlmTask task);
}

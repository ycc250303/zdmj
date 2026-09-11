package com.zdmj.common.async;

/**
 * 消费者 claim 成功后的执行上下文。{@code attachment} 为任务行（LLM / 向量任务实体）。
 */
public record StreamClaim(long taskId, long userId, Object attachment) {
}

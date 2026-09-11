package com.zdmj.matchService.support;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.async.AsyncLlmTask;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskPayloads;
import com.zdmj.common.async.AsyncTaskType;
import com.zdmj.matchService.dto.JobStudentMatchGenerateRequest;
import com.zdmj.matchService.service.JobStudentMatchService;

import lombok.RequiredArgsConstructor;

/**
 * 人岗匹配异步执行器：payload 须含 {@code jobId}，可选 {@code weights}。
 */
@Component
@RequiredArgsConstructor
public class JobMatchAsyncExecutor implements AsyncTaskExecutor {

    private final JobStudentMatchService jobStudentMatchService;
    private final ObjectMapper objectMapper;

    @Override
    public AsyncTaskType type() {
        return AsyncTaskType.JOB_MATCH;
    }

    /**
     * 调用现有 {@code generate}；缺岗位画像由该方法内同步生成，禁止再 enqueue。
     */
    @Override
    public String execute(AsyncLlmTask task) {
        JsonNode node = AsyncTaskPayloads.tree(task.getPayload(), objectMapper);
        long jobId = AsyncTaskPayloads.requireId(node, "jobId");
        JobStudentMatchGenerateRequest req = AsyncTaskPayloads.convertRemoving(
                node, JobStudentMatchGenerateRequest.class, objectMapper, "jobId");
        jobStudentMatchService.generate(jobId, req);
        return null;
    }
}

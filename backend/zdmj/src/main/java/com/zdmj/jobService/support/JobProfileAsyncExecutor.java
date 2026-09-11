package com.zdmj.jobService.support;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.async.AsyncLlmTask;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskPayloads;
import com.zdmj.common.async.AsyncTaskType;
import com.zdmj.jobService.service.JobCapabilityProfileService;

import lombok.RequiredArgsConstructor;

/**
 * 岗位能力画像异步执行器：payload 须含 {@code jobId}。
 */
@Component
@RequiredArgsConstructor
public class JobProfileAsyncExecutor implements AsyncTaskExecutor {

    private final JobCapabilityProfileService jobCapabilityProfileService;
    private final ObjectMapper objectMapper;

    @Override
    public AsyncTaskType type() {
        return AsyncTaskType.JOB_PROFILE;
    }

    /**
     * 调用现有生成方法（按当前 UserHolder 用户隔离落库），不写任务 result。
     */
    @Override
    public String execute(AsyncLlmTask task) {
        JsonNode node = AsyncTaskPayloads.tree(task.getPayload(), objectMapper);
        long jobId = AsyncTaskPayloads.requireId(node, "jobId");
        jobCapabilityProfileService.getJobCapabilityProfile(jobId);
        return null;
    }
}

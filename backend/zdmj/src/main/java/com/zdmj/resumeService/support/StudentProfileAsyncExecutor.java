package com.zdmj.resumeService.support;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.async.AsyncLlmTask;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskPayloads;
import com.zdmj.common.async.AsyncTaskType;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;

import lombok.RequiredArgsConstructor;

/**
 * 学生能力画像异步执行器：payload 为 {@link CapabilityProfileGenerateRequest}。
 */
@Component
@RequiredArgsConstructor
public class StudentProfileAsyncExecutor implements AsyncTaskExecutor {

    private final StudentCapabilityProfileService studentCapabilityProfileService;
    private final ObjectMapper objectMapper;

    @Override
    public AsyncTaskType type() {
        return AsyncTaskType.STUDENT_PROFILE;
    }

    /**
     * 调用现有 {@code generateProfile}，画像写入业务表，不写任务 result。
     */
    @Override
    public String execute(AsyncLlmTask task) {
        CapabilityProfileGenerateRequest request = AsyncTaskPayloads.read(
                task.getPayload(), CapabilityProfileGenerateRequest.class, objectMapper);
        studentCapabilityProfileService.generateProfile(request);
        return null;
    }
}

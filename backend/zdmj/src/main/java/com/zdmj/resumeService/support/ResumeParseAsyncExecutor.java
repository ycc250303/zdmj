package com.zdmj.resumeService.support;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.async.AsyncLlmTask;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskPayloads;
import com.zdmj.common.async.AsyncTaskType;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import com.zdmj.resumeService.service.ResumeService;

import lombok.RequiredArgsConstructor;

/**
 * 简历识别异步执行器：payload 为 {@link ResumeImportParseRequest}，结果写入任务 {@code result}。
 */
@Component
@RequiredArgsConstructor
public class ResumeParseAsyncExecutor implements AsyncTaskExecutor {

    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;

    @Override
    public AsyncTaskType type() {
        return AsyncTaskType.RESUME_PARSE;
    }

    /**
     * 调用现有 {@code parseImport}；无独立业务表，把结构化结果写入任务 result。
     */
    @Override
    public String execute(AsyncLlmTask task) {
        ResumeImportParseRequest request = AsyncTaskPayloads.read(
                task.getPayload(), ResumeImportParseRequest.class, objectMapper);
        ResumeImportParseResponse parsed = resumeService.parseImport(request);
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("简历识别结果序列化失败", e);
        }
    }
}

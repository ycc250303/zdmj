package com.zdmj.careerReportService.support;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.careerReportService.dto.CareerReportGenerateRequest;
import com.zdmj.careerReportService.service.CareerDevelopmentReportService;
import com.zdmj.common.async.AsyncLlmTask;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskPayloads;
import com.zdmj.common.async.AsyncTaskType;

import lombok.RequiredArgsConstructor;

/**
 * 职业发展报告生成执行器：payload 须含 {@code jobId}，可选偏好字段。
 */
@Component
@RequiredArgsConstructor
public class CareerReportAsyncExecutor implements AsyncTaskExecutor {

    private final CareerDevelopmentReportService careerDevelopmentReportService;
    private final ObjectMapper objectMapper;

    @Override
    public AsyncTaskType type() {
        return AsyncTaskType.CAREER_REPORT;
    }

    /**
     * 调用现有 {@code generate}；缺依赖由该方法内同步加载，禁止再 enqueue。
     */
    @Override
    public String execute(AsyncLlmTask task) {
        JsonNode node = AsyncTaskPayloads.tree(task.getPayload(), objectMapper);
        long jobId = AsyncTaskPayloads.requireId(node, "jobId");
        CareerReportGenerateRequest req = AsyncTaskPayloads.convertRemoving(
                node, CareerReportGenerateRequest.class, objectMapper, "jobId");
        careerDevelopmentReportService.generate(jobId, req);
        return null;
    }
}

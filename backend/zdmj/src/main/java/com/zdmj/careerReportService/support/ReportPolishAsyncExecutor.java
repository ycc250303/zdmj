package com.zdmj.careerReportService.support;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.careerReportService.dto.CareerReportPolishRequest;
import com.zdmj.careerReportService.service.CareerDevelopmentReportService;
import com.zdmj.common.async.AsyncLlmTask;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskPayloads;
import com.zdmj.common.async.AsyncTaskType;

import lombok.RequiredArgsConstructor;

/**
 * 报告润色执行器：payload 须含 {@code reportId}，可选 {@code instruction}。
 */
@Component
@RequiredArgsConstructor
public class ReportPolishAsyncExecutor implements AsyncTaskExecutor {

    private final CareerDevelopmentReportService careerDevelopmentReportService;
    private final ObjectMapper objectMapper;

    @Override
    public AsyncTaskType type() {
        return AsyncTaskType.REPORT_POLISH;
    }

    /**
     * 调用现有 {@code polish}，写入新版本报告。
     */
    @Override
    public String execute(AsyncLlmTask task) {
        JsonNode node = AsyncTaskPayloads.tree(task.getPayload(), objectMapper);
        long reportId = AsyncTaskPayloads.requireId(node, "reportId");
        CareerReportPolishRequest req = AsyncTaskPayloads.convertRemoving(
                node, CareerReportPolishRequest.class, objectMapper, "reportId");
        careerDevelopmentReportService.polish(reportId, req);
        return null;
    }
}

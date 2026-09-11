package com.zdmj.careerReportService.support;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.careerReportService.service.CareerDevelopmentReportService;
import com.zdmj.common.async.AsyncLlmTask;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskPayloads;
import com.zdmj.common.async.AsyncTaskType;

import lombok.RequiredArgsConstructor;

/**
 * 报告完整性检查执行器：payload 须含 {@code reportId}。
 */
@Component
@RequiredArgsConstructor
public class ReportCheckAsyncExecutor implements AsyncTaskExecutor {

    private final CareerDevelopmentReportService careerDevelopmentReportService;
    private final ObjectMapper objectMapper;

    @Override
    public AsyncTaskType type() {
        return AsyncTaskType.REPORT_CHECK;
    }

    /**
     * 调用现有 {@code checkIntegrity}，结果写回当前报告。
     */
    @Override
    public String execute(AsyncLlmTask task) {
        JsonNode node = AsyncTaskPayloads.tree(task.getPayload(), objectMapper);
        long reportId = AsyncTaskPayloads.requireId(node, "reportId");
        careerDevelopmentReportService.checkIntegrity(reportId);
        return null;
    }
}

package com.zdmj.careerReportService.support;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.careerReportService.dto.CareerReportCheckResponse;
import com.zdmj.careerReportService.dto.CareerReportGenerateRequest;
import com.zdmj.careerReportService.dto.CareerReportPolishRequest;
import com.zdmj.careerReportService.dto.CareerReportResponse;
import com.zdmj.careerReportService.service.CareerDevelopmentReportService;
import com.zdmj.common.async.AsyncLlmTask;

@ExtendWith(MockitoExtension.class)
class CareerReportAsyncExecutorTest {

    @Mock
    private CareerDevelopmentReportService reportService;

    private ObjectMapper mapper;
    private CareerReportAsyncExecutor generateExecutor;
    private ReportPolishAsyncExecutor polishExecutor;
    private ReportCheckAsyncExecutor checkExecutor;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        generateExecutor = new CareerReportAsyncExecutor(reportService, mapper);
        polishExecutor = new ReportPolishAsyncExecutor(reportService, mapper);
        checkExecutor = new ReportCheckAsyncExecutor(reportService, mapper);
    }

    @Test
    void generate_shouldCallService() {
        when(reportService.generate(eq(3L), any(CareerReportGenerateRequest.class)))
                .thenReturn(new CareerReportResponse());
        AsyncLlmTask task = new AsyncLlmTask();
        task.setPayload("{\"jobId\":3,\"focus\":\"算法\"}");

        assertNull(generateExecutor.execute(task));
        verify(reportService).generate(eq(3L), any(CareerReportGenerateRequest.class));
    }

    @Test
    void polish_shouldCallService() {
        when(reportService.polish(eq(11L), any(CareerReportPolishRequest.class)))
                .thenReturn(new CareerReportResponse());
        AsyncLlmTask task = new AsyncLlmTask();
        task.setPayload("{\"reportId\":11,\"instruction\":\"更简洁\"}");

        assertNull(polishExecutor.execute(task));
        verify(reportService).polish(eq(11L), any(CareerReportPolishRequest.class));
    }

    @Test
    void check_shouldCallService() {
        when(reportService.checkIntegrity(11L)).thenReturn(new CareerReportCheckResponse());
        AsyncLlmTask task = new AsyncLlmTask();
        task.setPayload("{\"reportId\":11}");

        assertNull(checkExecutor.execute(task));
        verify(reportService).checkIntegrity(11L);
    }
}

package com.zdmj.matchService.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.async.AsyncLlmTask;
import com.zdmj.common.async.AsyncTaskType;
import com.zdmj.matchService.dto.JobStudentMatchGenerateRequest;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.service.JobStudentMatchService;

@ExtendWith(MockitoExtension.class)
class JobMatchAsyncExecutorTest {

    @Mock
    private JobStudentMatchService jobStudentMatchService;

    private JobMatchAsyncExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JobMatchAsyncExecutor(jobStudentMatchService, new ObjectMapper());
    }

    @Test
    void execute_shouldCallGenerateWithoutEnqueue() {
        when(jobStudentMatchService.generate(eq(3L), any(JobStudentMatchGenerateRequest.class)))
                .thenReturn(new JobStudentMatchResponse());
        AsyncLlmTask task = new AsyncLlmTask();
        task.setPayload("{\"jobId\":3}");

        assertNull(executor.execute(task));
        verify(jobStudentMatchService).generate(eq(3L), any(JobStudentMatchGenerateRequest.class));
    }

    @Test
    void execute_withWeights_shouldKeepWeightsOnRequest() {
        when(jobStudentMatchService.generate(eq(3L), any(JobStudentMatchGenerateRequest.class)))
                .thenReturn(new JobStudentMatchResponse());
        AsyncLlmTask task = new AsyncLlmTask();
        task.setPayload("{\"jobId\":3,\"weights\":{\"basic\":0.4,\"professionalSkill\":0.3,"
                + "\"professionalQuality\":0.2,\"developmentPotential\":0.1}}");

        assertNull(executor.execute(task));
        ArgumentCaptor<JobStudentMatchGenerateRequest> captor =
                ArgumentCaptor.forClass(JobStudentMatchGenerateRequest.class);
        verify(jobStudentMatchService).generate(eq(3L), captor.capture());
        assertEquals(0, new BigDecimal("0.4").compareTo(captor.getValue().getWeights().getBasic()));
    }

    @Test
    void type_shouldBeJobMatch() {
        assertEquals(AsyncTaskType.JOB_MATCH, executor.type());
    }
}

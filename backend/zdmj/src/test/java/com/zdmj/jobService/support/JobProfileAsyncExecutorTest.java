package com.zdmj.jobService.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.async.AsyncLlmTask;
import com.zdmj.common.async.AsyncTaskType;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.service.JobCapabilityProfileService;

@ExtendWith(MockitoExtension.class)
class JobProfileAsyncExecutorTest {

    @Mock
    private JobCapabilityProfileService jobCapabilityProfileService;

    private JobProfileAsyncExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JobProfileAsyncExecutor(jobCapabilityProfileService, new ObjectMapper());
    }

    @Test
    void execute_shouldCallGenerate() {
        when(jobCapabilityProfileService.getJobCapabilityProfile(3L)).thenReturn(new JobCapabilityProfileResponse());
        AsyncLlmTask task = new AsyncLlmTask();
        task.setPayload("{\"jobId\":3}");

        assertNull(executor.execute(task));
        verify(jobCapabilityProfileService).getJobCapabilityProfile(3L);
    }

    @Test
    void type_shouldBeJobProfile() {
        assertEquals(AsyncTaskType.JOB_PROFILE, executor.type());
    }
}

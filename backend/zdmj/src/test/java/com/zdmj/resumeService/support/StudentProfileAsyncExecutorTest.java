package com.zdmj.resumeService.support;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;

@ExtendWith(MockitoExtension.class)
class StudentProfileAsyncExecutorTest {

    @Mock
    private StudentCapabilityProfileService studentCapabilityProfileService;

    private StudentProfileAsyncExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new StudentProfileAsyncExecutor(studentCapabilityProfileService, new ObjectMapper());
    }

    @Test
    void execute_shouldCallGenerateProfile() {
        when(studentCapabilityProfileService.generateProfile(any(CapabilityProfileGenerateRequest.class)))
                .thenReturn(new StudentCapabilityProfileResponse());
        AsyncLlmTask task = new AsyncLlmTask();
        task.setPayload("{\"rawText\":\"简历\"}");

        assertNull(executor.execute(task));
        verify(studentCapabilityProfileService).generateProfile(any(CapabilityProfileGenerateRequest.class));
    }

    @Test
    void type_shouldBeStudentProfile() {
        org.junit.jupiter.api.Assertions.assertEquals(AsyncTaskType.STUDENT_PROFILE, executor.type());
    }
}

package com.zdmj.matchService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.common.async.AsyncTaskDTO;
import com.zdmj.common.async.AsyncTaskService;
import com.zdmj.common.async.AsyncTaskType;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobService;
import com.zdmj.matchService.mapper.JobStudentMatchMapper;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;

@ExtendWith(MockitoExtension.class)
class JobStudentMatchEnqueueTest {

    @Mock
    private JobService jobService;
    @Mock
    private JobCapabilityProfileService jobCapabilityProfileService;
    @Mock
    private StudentCapabilityProfileService studentCapabilityProfileService;
    @Mock
    private ChatUtil chatUtil;
    @Mock
    private JobStudentMatchMapper matchMapper;
    @Mock
    private AsyncTaskService asyncTaskService;

    private JobStudentMatchServiceImpl matchService;

    @BeforeEach
    void setUp() {
        matchService = new JobStudentMatchServiceImpl(
                jobService,
                jobCapabilityProfileService,
                studentCapabilityProfileService,
                chatUtil,
                new ObjectMapper(),
                new PromptUtil(new DefaultResourceLoader()),
                asyncTaskService);
        ReflectionTestUtils.setField(matchService, "baseMapper", matchMapper);
        UserHolder.set(UserContext.of(8L, "u"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void enqueue_missingStudentProfile_shouldThrow11002WithoutEnqueue() {
        when(jobService.getDetail(3L)).thenReturn(new JobListItemResponse());
        when(studentCapabilityProfileService.getCurrentUserProfileOrNull()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> matchService.enqueueGenerate(3L, null));

        assertEquals(ErrorCode.MATCH_PRECONDITION_MISSING.getCode(), ex.getCode());
        verify(asyncTaskService, never()).enqueue(any(), any(Long.class), any(), any());
        verify(jobCapabilityProfileService, never()).getJobCapabilityProfile(any());
    }

    @Test
    void enqueue_ok_shouldCallAsyncTaskService() {
        when(jobService.getDetail(3L)).thenReturn(new JobListItemResponse());
        when(studentCapabilityProfileService.getCurrentUserProfileOrNull())
                .thenReturn(new StudentCapabilityProfileResponse());
        AsyncTaskDTO dto = new AsyncTaskDTO();
        dto.setTaskId(99L);
        when(asyncTaskService.enqueue(eq(AsyncTaskType.JOB_MATCH), eq(8L), eq("user:8:job:3"), any()))
                .thenReturn(dto);

        AsyncTaskDTO result = matchService.enqueueGenerate(3L, null);

        assertEquals(99L, result.getTaskId());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(asyncTaskService).enqueue(eq(AsyncTaskType.JOB_MATCH), eq(8L), eq("user:8:job:3"), payload.capture());
        org.junit.jupiter.api.Assertions.assertTrue(payload.getValue().contains("\"jobId\":3"));
    }
}

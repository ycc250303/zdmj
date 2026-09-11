package com.zdmj.jobService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

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
import com.zdmj.jobService.service.JobService;

@ExtendWith(MockitoExtension.class)
class JobCapabilityProfileEnqueueTest {

    @Mock
    private JobService jobService;
    @Mock
    private ChatUtil chatUtil;
    @Mock
    private AsyncTaskService asyncTaskService;

    private JobCapabilityProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobCapabilityProfileServiceImpl(
                jobService, chatUtil, new PromptUtil(new DefaultResourceLoader()), asyncTaskService);
        UserHolder.set(UserContext.of(8L, "u"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void enqueue_jobMissing_shouldThrow10001() {
        when(jobService.getDetail(3L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.enqueueGenerate(3L));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(asyncTaskService, never()).enqueue(any(), any(Long.class), any(), any());
    }

    @Test
    void enqueue_ok_shouldUseUserJobBizKey() {
        when(jobService.getDetail(3L)).thenReturn(new JobListItemResponse());
        AsyncTaskDTO dto = new AsyncTaskDTO();
        dto.setTaskId(11L);
        when(asyncTaskService.enqueue(AsyncTaskType.JOB_PROFILE, 8L, "user:8:job:3", "{\"jobId\":3}"))
                .thenReturn(dto);

        assertEquals(11L, service.enqueueGenerate(3L).getTaskId());
    }
}

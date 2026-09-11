package com.zdmj.resumeService.service.impl;

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
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.mapper.StudentCapabilityProfileMapper;

@ExtendWith(MockitoExtension.class)
class StudentCapabilityProfileEnqueueTest {

    @Mock
    private ChatUtil chatUtil;
    @Mock
    private FileUploadService fileUploadService;
    @Mock
    private PdfParserUtil pdfParserUtil;
    @Mock
    private StudentCapabilityProfileMapper mapper;
    @Mock
    private AsyncTaskService asyncTaskService;

    private StudentCapabilityProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentCapabilityProfileServiceImpl(chatUtil, new ObjectMapper(), fileUploadService,
                pdfParserUtil, new PromptUtil(new DefaultResourceLoader()), asyncTaskService);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        UserHolder.set(UserContext.of(8L, "u"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void enqueue_missingSource_shouldThrowWithoutEnqueue() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.enqueueGenerate(req));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(asyncTaskService, never()).enqueue(any(), any(Long.class), any(), any());
    }

    @Test
    void enqueue_ok_shouldNotCallChatUtil() {
        CapabilityProfileGenerateRequest req = new CapabilityProfileGenerateRequest();
        req.setRawText("简历正文足够长");
        AsyncTaskDTO dto = new AsyncTaskDTO();
        dto.setTaskId(5L);
        when(asyncTaskService.enqueue(any(), any(Long.class), any(), any())).thenReturn(dto);

        assertEquals(5L, service.enqueueGenerate(req).getTaskId());
        verify(asyncTaskService).enqueue(eq(AsyncTaskType.STUDENT_PROFILE), eq(8L), eq("user:8"),
                org.mockito.ArgumentMatchers.contains("简历正文足够长"));
        verify(chatUtil, never()).chatStructuredOnce(any(), any(), any(), any(), any());
    }
}

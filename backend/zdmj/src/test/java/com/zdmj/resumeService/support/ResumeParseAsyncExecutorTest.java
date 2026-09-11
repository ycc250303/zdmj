package com.zdmj.resumeService.support;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import com.zdmj.resumeService.service.ResumeService;

@ExtendWith(MockitoExtension.class)
class ResumeParseAsyncExecutorTest {

    @Mock
    private ResumeService resumeService;

    private ResumeParseAsyncExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ResumeParseAsyncExecutor(resumeService, new ObjectMapper());
    }

    @Test
    void execute_shouldSerializeParseResult() {
        ResumeImportParseResponse parsed = new ResumeImportParseResponse();
        parsed.getWarnings().add("ok");
        when(resumeService.parseImport(any(ResumeImportParseRequest.class))).thenReturn(parsed);
        AsyncLlmTask task = new AsyncLlmTask();
        task.setPayload("{\"rawText\":\"简历正文\"}");

        String result = executor.execute(task);

        assertTrue(result.contains("ok"));
        verify(resumeService).parseImport(any(ResumeImportParseRequest.class));
    }
}

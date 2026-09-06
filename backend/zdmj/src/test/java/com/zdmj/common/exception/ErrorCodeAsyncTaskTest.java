package com.zdmj.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeAsyncTaskTest {

    @Test
    void asyncTaskNotFound_shouldBe404With13xxx() {
        assertEquals(13001, ErrorCode.ASYNC_TASK_NOT_FOUND.getCode());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.ASYNC_TASK_NOT_FOUND.getHttpStatus());
        assertNotEquals(ErrorCode.ASYNC_TASK_NOT_FOUND.getCode(), ErrorCode.CAREER_REPORT_INVALID.getCode());
    }

    @Test
    void asyncTaskFailed_shouldBe400With13xxx() {
        assertEquals(13002, ErrorCode.ASYNC_TASK_FAILED.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.ASYNC_TASK_FAILED.getHttpStatus());
    }
}

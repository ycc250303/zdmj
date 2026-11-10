package com.zdmj.resumeService.utils;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateRangeUtilTest {

    @Test
    void rejectIfEndBeforeStart_whenEndBeforeStart_shouldThrowGivenErrorCode() {
        BusinessException ex = assertThrows(BusinessException.class, () -> DateRangeUtil.rejectIfEndBeforeStart(
                LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1), ErrorCode.CAREER_LEAVE_TIME_INVALID));

        assertEquals(ErrorCode.CAREER_LEAVE_TIME_INVALID.getCode(), ex.getCode());
    }

    @Test
    void rejectIfEndBeforeStart_whenEitherDateMissingOrEndNotBeforeStart_shouldPass() {
        assertDoesNotThrow(() -> DateRangeUtil.rejectIfEndBeforeStart(
                null, LocalDate.of(2024, 1, 1), ErrorCode.PROJECT_END_TIME_INVALID));
        assertDoesNotThrow(() -> DateRangeUtil.rejectIfEndBeforeStart(
                LocalDate.of(2024, 1, 1), null, ErrorCode.EDUCATION_GRADUATE_TIME_INVALID));
        assertDoesNotThrow(() -> DateRangeUtil.rejectIfEndBeforeStart(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1), ErrorCode.CAREER_LEAVE_TIME_INVALID));
    }
}

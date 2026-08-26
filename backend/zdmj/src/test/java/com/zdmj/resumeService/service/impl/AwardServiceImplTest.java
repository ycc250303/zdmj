package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.AwardRequest;
import com.zdmj.resumeService.dto.AwardResponse;
import com.zdmj.resumeService.entity.Award;
import com.zdmj.resumeService.mapper.AwardMapper;
import com.zdmj.resumeService.mapper.AwardStructMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AwardServiceImplTest {

    @Mock
    private AwardMapper awardMapper;
    @Mock
    private AwardStructMapper awardStructMapper;

    private AwardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new AwardServiceImpl(awardStructMapper));
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "baseMapper", awardMapper);
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void create_success_shouldSaveAward() {
        AwardRequest dto = new AwardRequest();
        dto.setAwardType(2);
        dto.setName("数学建模省一等奖");
        dto.setAwardDate(LocalDate.of(2023, 11, 1));
        doReturn(true).when(service).save(any(Award.class));

        AwardResponse out = service.create(dto);

        assertEquals(1L, out.getUserId());
        assertEquals("数学建模省一等奖", out.getName());
        verify(service).save(any(Award.class));
    }

    @Test
    void create_invalidType_shouldThrow() {
        AwardRequest dto = new AwardRequest();
        dto.setAwardType(9);
        dto.setName("无效类型");
        dto.setAwardDate(LocalDate.of(2023, 1, 1));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.AWARD_TYPE_INVALID.getCode(), ex.getCode());
    }
}

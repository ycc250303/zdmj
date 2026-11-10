package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.SkillRequest;
import com.zdmj.resumeService.dto.SkillResponse;
import com.zdmj.resumeService.entity.Skill;
import com.zdmj.resumeService.mapper.SkillMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SkillServiceImplTest {

    @Mock
    private SkillMapper skillMapper;

    private SkillServiceImpl service;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = spy(new SkillServiceImpl(validator));
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "baseMapper", skillMapper);
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void create_success_shouldTrimNameAndClearBlankDescription() {
        SkillRequest dto = new SkillRequest();
        dto.setName("  Java  ");
        dto.setDescription("  ");
        doReturn(true).when(service).save(any(Skill.class));

        SkillResponse out = service.create(dto);

        assertEquals("Java", out.getName());
        assertNull(out.getDescription());
        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(service).save(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals("Java", captor.getValue().getName());
        assertNull(captor.getValue().getDescription());
    }

    @Test
    void create_userNotLogin_shouldThrowAndSkipSave() {
        UserHolder.clear();
        SkillRequest dto = validRequest();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(service, never()).save(any(Skill.class));
    }

    @Test
    void create_blankName_shouldThrowAndSkipSave() {
        SkillRequest dto = new SkillRequest();
        dto.setName(" ");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("技能名称不能为空"));
        verify(service, never()).save(any(Skill.class));
    }

    @Test
    void create_descriptionTooLong_shouldThrowAndSkipSave() {
        SkillRequest dto = new SkillRequest();
        dto.setName("Java");
        dto.setDescription("说".repeat(501));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("技能说明长度不能超过500个字符"));
        verify(service, never()).save(any(Skill.class));
    }

    @Test
    void create_saveFailed_shouldThrowAddFailed() {
        doReturn(false).when(service).save(any(Skill.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(validRequest()));

        assertEquals(ErrorCode.SKILL_ADD_FAILED.getCode(), ex.getCode());
    }

    @Test
    void update_notOwner_shouldThrowAndSkipUpdateById() {
        SkillRequest dto = validRequest();
        dto.setId(10L);
        Skill existing = new Skill();
        existing.setId(10L);
        existing.setUserId(2L);
        doReturn(existing).when(skillMapper).selectById(10L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(service, never()).updateById(any(Skill.class));
    }

    @Test
    void update_success_shouldReplaceNameAndClearDescription() {
        SkillRequest dto = new SkillRequest();
        dto.setId(10L);
        dto.setName("Spring");
        dto.setDescription("");
        Skill existing = new Skill();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("Java");
        existing.setDescription("旧说明");
        doReturn(existing).when(skillMapper).selectById(10L);
        doReturn(true).when(service).updateById(any(Skill.class));

        SkillResponse out = service.update(dto);

        assertEquals("Spring", out.getName());
        assertNull(out.getDescription());
        verify(service).updateById(existing);
    }

    @Test
    void update_updateFailed_shouldThrowUpdateFailed() {
        SkillRequest dto = validRequest();
        dto.setId(10L);
        Skill existing = new Skill();
        existing.setId(10L);
        existing.setUserId(1L);
        doReturn(existing).when(skillMapper).selectById(10L);
        doReturn(false).when(service).updateById(any(Skill.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.SKILL_UPDATE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void delete_removeFailed_shouldThrowDeleteFailed() {
        Skill existing = new Skill();
        existing.setId(11L);
        existing.setUserId(1L);
        doReturn(existing).when(skillMapper).selectById(11L);
        doReturn(false).when(service).removeById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(11L));

        assertEquals(ErrorCode.SKILL_DELETE_FAILED.getCode(), ex.getCode());
        verify(service).removeById(11L);
    }

    @Test
    void delete_notOwner_shouldThrowNoPermission() {
        Skill existing = new Skill();
        existing.setId(11L);
        existing.setUserId(2L);
        doReturn(existing).when(skillMapper).selectById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(11L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(service, never()).removeById(11L);
    }

    private static SkillRequest validRequest() {
        SkillRequest dto = new SkillRequest();
        dto.setName("Java");
        dto.setDescription("熟悉 Spring");
        return dto;
    }
}

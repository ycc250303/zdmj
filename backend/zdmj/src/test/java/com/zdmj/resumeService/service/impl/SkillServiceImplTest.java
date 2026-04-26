package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.SkillDTO;
import com.zdmj.resumeService.dto.SkillItemDTO;
import com.zdmj.resumeService.entity.Skill;
import com.zdmj.resumeService.mapper.SkillMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        service = spy(new SkillServiceImpl());
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "baseMapper", skillMapper);
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void create_success_shouldValidateAndSave() {
        SkillDTO dto = new SkillDTO();
        dto.setName("backend");
        dto.setContent(validContent());
        doReturn(true).when(service).save(any(Skill.class));

        Skill out = service.create(dto);

        assertEquals(1L, out.getUserId());
        assertEquals("backend", out.getName());
        assertEquals(1, out.getContent().size());
        verify(service).save(any(Skill.class));
    }

    @Test
    void create_invalidContent_shouldThrowAndSkipSave() {
        SkillDTO dto = new SkillDTO();
        dto.setName("backend");
        SkillItemDTO invalid = new SkillItemDTO();
        invalid.setType(" ");
        invalid.setContent(List.of("Java"));
        dto.setContent(List.of(invalid));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(service, never()).save(any(Skill.class));
    }

    @Test
    void create_saveFailed_shouldThrowAddFailed() {
        SkillDTO dto = new SkillDTO();
        dto.setName("backend");
        doReturn(false).when(service).save(any(Skill.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.SKILL_ADD_FAILED.getCode(), ex.getCode());
    }

    @Test
    void getById_notFound_shouldThrow() {
        doReturn(null).when(skillMapper).selectById(9L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(9L));

        assertEquals(ErrorCode.SKILL_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getByUserId_shouldCallMapper() {
        doReturn(List.of(new Skill())).when(skillMapper).selectByUserId(1L);

        List<Skill> out = service.getByUserId();

        assertEquals(1, out.size());
        verify(skillMapper).selectByUserId(1L);
    }

    @Test
    void update_notOwner_shouldThrowAndSkipUpdateById() {
        SkillDTO dto = new SkillDTO();
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
    void update_success_shouldUpdateNameAndContent() {
        SkillDTO dto = new SkillDTO();
        dto.setId(10L);
        dto.setName("new-skill");
        dto.setContent(validContent());
        Skill existing = new Skill();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("old-skill");
        doReturn(existing).when(skillMapper).selectById(10L);
        doReturn(true).when(service).updateById(any(Skill.class));

        Skill out = service.update(dto);

        assertEquals("new-skill", out.getName());
        assertEquals(1, out.getContent().size());
        verify(service).updateById(existing);
    }

    @Test
    void update_updateFailed_shouldThrowUpdateFailed() {
        SkillDTO dto = new SkillDTO();
        dto.setId(10L);
        dto.setName("new-skill");
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

    private List<SkillItemDTO> validContent() {
        SkillItemDTO item = new SkillItemDTO();
        item.setType("后端");
        item.setContent(List.of("Java", "Spring Boot"));
        return List.of(item);
    }
}

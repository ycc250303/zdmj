package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.ProjectExperienceRequest;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.dto.ProjectExperienceResponse;
import com.zdmj.resumeService.enums.ProjectStatusEnum;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceStructMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectExperienceServiceImplTest {

    @Mock
    private ProjectExperienceMapper projectExperienceMapper;
    @Mock
    private ProjectExperienceStructMapper projectExperienceStructMapper;

    private ProjectExperienceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new ProjectExperienceServiceImpl(projectExperienceStructMapper));
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "baseMapper", projectExperienceMapper);
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void create_success_shouldSetDefaultStatusAndSave() {
        ProjectExperienceRequest dto = new ProjectExperienceRequest();
        dto.setName("Resume Parser");
        dto.setRole("Backend");
        dto.setDescription("desc");
        dto.setContribution("contrib");
        doReturn(true).when(service).save(any(ProjectExperience.class));

        ProjectExperienceResponse out = service.create(dto);

        assertEquals(1L, out.getUserId());
        assertEquals(ProjectStatusEnum.COMMITTED.getCode(), out.getStatus());
        assertNull(out.getLookupResult());
        verify(service).save(any(ProjectExperience.class));
    }

    @Test
    void create_saveFailed_shouldThrowAddFailed() {
        ProjectExperienceRequest dto = new ProjectExperienceRequest();
        dto.setName("Resume Parser");
        doReturn(false).when(service).save(any(ProjectExperience.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.PROJECT_EXPERIENCE_ADD_FAILED.getCode(), ex.getCode());
    }

    @Test
    void getById_notFound_shouldThrow() {
        doReturn(null).when(projectExperienceMapper).selectById(7L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(7L));

        assertEquals(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getByUserId_shouldCallMapper() {
        doReturn(List.of(new ProjectExperience())).when(projectExperienceMapper).selectByUserId(1L);

        List<ProjectExperienceResponse> out = service.getByUserId();

        assertEquals(1, out.size());
        verify(projectExperienceMapper).selectByUserId(1L);
    }

    @Test
    void update_notOwner_shouldThrowNoPermission() {
        ProjectExperienceRequest dto = new ProjectExperienceRequest();
        dto.setId(10L);
        ProjectExperience existing = new ProjectExperience();
        existing.setId(10L);
        existing.setUserId(2L);
        doReturn(existing).when(projectExperienceMapper).selectById(10L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(projectExperienceStructMapper, never())
                .updateEntityFromDto(any(ProjectExperienceRequest.class), any(ProjectExperience.class));
        verify(service, never()).updateById(any(ProjectExperience.class));
    }

    @Test
    void update_invalidDate_shouldThrowAndSkipUpdate() {
        ProjectExperienceRequest dto = new ProjectExperienceRequest();
        dto.setId(10L);
        ProjectExperience existing = new ProjectExperience();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setStartDate(LocalDate.of(2020, 1, 1));
        existing.setEndDate(LocalDate.of(2021, 1, 1));
        doReturn(existing).when(projectExperienceMapper).selectById(10L);
        doAnswer(invocation -> {
            ProjectExperience argEntity = invocation.getArgument(1);
            argEntity.setStartDate(LocalDate.of(2025, 1, 1));
            argEntity.setEndDate(LocalDate.of(2024, 1, 1));
            return null;
        }).when(projectExperienceStructMapper)
                .updateEntityFromDto(any(ProjectExperienceRequest.class), any(ProjectExperience.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.PROJECT_END_TIME_INVALID.getCode(), ex.getCode());
        verify(service, never()).updateById(any(ProjectExperience.class));
    }

    @Test
    void update_success_shouldPatchAndUpdateById() {
        ProjectExperienceRequest dto = new ProjectExperienceRequest();
        dto.setId(10L);
        dto.setName("NewName");
        ProjectExperience existing = new ProjectExperience();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("OldName");
        doReturn(existing).when(projectExperienceMapper).selectById(10L);
        doAnswer(invocation -> {
            ProjectExperienceRequest argDto = invocation.getArgument(0);
            ProjectExperience argEntity = invocation.getArgument(1);
            argEntity.setName(argDto.getName());
            return null;
        }).when(projectExperienceStructMapper)
                .updateEntityFromDto(any(ProjectExperienceRequest.class), any(ProjectExperience.class));
        doReturn(true).when(service).updateById(any(ProjectExperience.class));

        ProjectExperienceResponse out = service.update(dto);

        assertEquals("NewName", out.getName());
        verify(projectExperienceStructMapper).updateEntityFromDto(dto, existing);
        verify(service).updateById(existing);
    }

    @Test
    void update_updateFailed_shouldThrowUpdateFailed() {
        ProjectExperienceRequest dto = new ProjectExperienceRequest();
        dto.setId(10L);
        dto.setName("NewName");
        ProjectExperience existing = new ProjectExperience();
        existing.setId(10L);
        existing.setUserId(1L);
        doReturn(existing).when(projectExperienceMapper).selectById(10L);
        doAnswer(invocation -> {
            ProjectExperienceRequest argDto = invocation.getArgument(0);
            ProjectExperience argEntity = invocation.getArgument(1);
            argEntity.setName(argDto.getName());
            return null;
        }).when(projectExperienceStructMapper)
                .updateEntityFromDto(any(ProjectExperienceRequest.class), any(ProjectExperience.class));
        doReturn(false).when(service).updateById(any(ProjectExperience.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.PROJECT_EXPERIENCE_UPDATE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void delete_removeFailed_shouldThrowDeleteFailed() {
        ProjectExperience existing = new ProjectExperience();
        existing.setId(11L);
        existing.setUserId(1L);
        doReturn(existing).when(projectExperienceMapper).selectById(11L);
        doReturn(false).when(service).removeById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(11L));

        assertEquals(ErrorCode.PROJECT_EXPERIENCE_DELETE_FAILED.getCode(), ex.getCode());
        verify(service).removeById(11L);
    }

    @Test
    void delete_notOwner_shouldThrowNoPermission() {
        ProjectExperience existing = new ProjectExperience();
        existing.setId(11L);
        existing.setUserId(2L);
        doReturn(existing).when(projectExperienceMapper).selectById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(11L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(service, never()).removeById(11L);
    }
}

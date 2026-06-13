package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.CareerDTO;
import com.zdmj.resumeService.entity.Career;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.mapper.CareerStructMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CareerServiceImplTest {

    @Mock
    private CareerMapper careerMapper;
    @Mock
    private CareerStructMapper careerStructMapper;

    private CareerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new CareerServiceImpl(careerStructMapper));
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "baseMapper", careerMapper);
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void create_success_shouldSaveCareer() {
        CareerDTO dto = new CareerDTO();
        dto.setCompany("ZDMJ");
        dto.setPosition("Java");
        dto.setStartDate(LocalDate.of(2022, 1, 1));
        dto.setEndDate(LocalDate.of(2023, 1, 1));
        doReturn(true).when(service).save(any(Career.class));

        Career out = service.create(dto);

        assertEquals(1L, out.getUserId());
        assertEquals("ZDMJ", out.getCompany());
        verify(service).save(any(Career.class));
    }

    @Test
    void create_saveFailed_shouldThrowAddFailed() {
        CareerDTO dto = new CareerDTO();
        dto.setCompany("ZDMJ");
        doReturn(false).when(service).save(any(Career.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.CAREER_ADD_FAILED.getCode(), ex.getCode());
    }

    @Test
    void getById_notFound_shouldThrow() {
        doReturn(null).when(careerMapper).selectById(9L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(9L));

        assertEquals(ErrorCode.CAREER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getByUserId_shouldCallMapper() {
        doReturn(List.of(new Career())).when(careerMapper).selectByUserId(1L);

        List<Career> out = service.getByUserId();

        assertEquals(1, out.size());
        verify(careerMapper).selectByUserId(1L);
    }

    @Test
    void update_noPermission_shouldThrow() {
        CareerDTO dto = new CareerDTO();
        dto.setId(10L);
        Career existing = new Career();
        existing.setId(10L);
        existing.setUserId(2L);
        doReturn(existing).when(careerMapper).selectById(10L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(careerStructMapper, never()).updateEntityFromDto(any(CareerDTO.class), any(Career.class));
        verify(service, never()).updateById(any(Career.class));
    }

    @Test
    void update_invalidDate_shouldThrowAndSkipUpdateById() {
        CareerDTO dto = new CareerDTO();
        dto.setId(10L);
        Career existing = new Career();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setStartDate(LocalDate.of(2020, 1, 1));
        existing.setEndDate(LocalDate.of(2021, 1, 1));
        doReturn(existing).when(careerMapper).selectById(10L);
        doAnswer(invocation -> {
            Career argEntity = invocation.getArgument(1);
            argEntity.setStartDate(LocalDate.of(2024, 1, 1));
            argEntity.setEndDate(LocalDate.of(2023, 1, 1));
            return null;
        }).when(careerStructMapper).updateEntityFromDto(any(CareerDTO.class), any(Career.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.CAREER_LEAVE_TIME_INVALID.getCode(), ex.getCode());
        verify(service, never()).updateById(any(Career.class));
    }

    @Test
    void update_success_shouldPatchAndPersist() {
        CareerDTO dto = new CareerDTO();
        dto.setId(10L);
        dto.setCompany("NewCo");
        Career existing = new Career();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setCompany("OldCo");
        doReturn(existing).when(careerMapper).selectById(10L);
        doAnswer(invocation -> {
            CareerDTO argDto = invocation.getArgument(0);
            Career argEntity = invocation.getArgument(1);
            argEntity.setCompany(argDto.getCompany());
            return null;
        }).when(careerStructMapper).updateEntityFromDto(any(CareerDTO.class), any(Career.class));
        doReturn(true).when(service).updateById(any(Career.class));

        Career out = service.update(dto);

        assertEquals("NewCo", out.getCompany());
        verify(careerStructMapper).updateEntityFromDto(dto, existing);
        verify(service).updateById(existing);
    }

    @Test
    void update_updateFailed_shouldThrowUpdateFailed() {
        CareerDTO dto = new CareerDTO();
        dto.setId(10L);
        dto.setCompany("NewCo");
        Career existing = new Career();
        existing.setId(10L);
        existing.setUserId(1L);
        doReturn(existing).when(careerMapper).selectById(10L);
        doAnswer(invocation -> {
            CareerDTO argDto = invocation.getArgument(0);
            Career argEntity = invocation.getArgument(1);
            argEntity.setCompany(argDto.getCompany());
            return null;
        }).when(careerStructMapper).updateEntityFromDto(any(CareerDTO.class), any(Career.class));
        doReturn(false).when(service).updateById(any(Career.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals(ErrorCode.CAREER_UPDATE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void delete_removeFailed_shouldThrowDeleteFailed() {
        Career existing = new Career();
        existing.setId(11L);
        existing.setUserId(1L);
        doReturn(existing).when(careerMapper).selectById(11L);
        doReturn(false).when(service).removeById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(11L));

        assertEquals(ErrorCode.CAREER_DELETE_FAILED.getCode(), ex.getCode());
        verify(service).removeById(11L);
    }

    @Test
    void delete_notOwner_shouldThrowNoPermission() {
        Career existing = new Career();
        existing.setId(11L);
        existing.setUserId(2L);
        doReturn(existing).when(careerMapper).selectById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(11L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(service, never()).removeById(11L);
    }
}

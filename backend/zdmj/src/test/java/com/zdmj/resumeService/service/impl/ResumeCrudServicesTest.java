package com.zdmj.resumeService.service.impl;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.CareerDTO;
import com.zdmj.resumeService.dto.EducationDTO;
import com.zdmj.resumeService.dto.SkillDTO;
import com.zdmj.resumeService.entity.Career;
import com.zdmj.resumeService.entity.Education;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.entity.Skill;
import com.zdmj.resumeService.mapper.CareerMapper;
import com.zdmj.resumeService.mapper.CareerStructMapper;
import com.zdmj.resumeService.mapper.EducationMapper;
import com.zdmj.resumeService.mapper.EducationStructMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;
import com.zdmj.resumeService.mapper.ProjectExperienceStructMapper;
import com.zdmj.resumeService.mapper.SkillMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumeCrudServicesTest {

    @Mock
    private EducationMapper educationMapper;
    @Mock
    private EducationStructMapper educationStructMapper;
    @Mock
    private CareerMapper careerMapper;
    @Mock
    private CareerStructMapper careerStructMapper;
    @Mock
    private ProjectExperienceMapper projectExperienceMapper;
    @Mock
    private ProjectExperienceStructMapper projectExperienceStructMapper;
    @Mock
    private SkillMapper skillMapper;

    private EducationServiceImpl educationService;
    private CareerServiceImpl careerService;
    private ProjectExperienceServiceImpl projectExperienceService;
    private SkillServiceImpl skillService;

    @BeforeEach
    void setUp() {
        educationService = spy(new EducationServiceImpl(educationStructMapper));
        careerService = spy(new CareerServiceImpl(careerStructMapper));
        projectExperienceService = spy(new ProjectExperienceServiceImpl(projectExperienceStructMapper));
        skillService = spy(new SkillServiceImpl());

        ReflectionTestUtils.setField(educationService, "baseMapper", educationMapper);
        ReflectionTestUtils.setField(careerService, "baseMapper", careerMapper);
        ReflectionTestUtils.setField(projectExperienceService, "baseMapper", projectExperienceMapper);
        ReflectionTestUtils.setField(skillService, "baseMapper", skillMapper);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void educationUpdate_notOwner_shouldThrowNoPermissionAndSkipUpdate() {
        UserHolder.set(UserContext.of(1L, "u1"));
        EducationDTO dto = new EducationDTO();
        dto.setId(10L);
        dto.setSchool("new-school");
        Education existing = new Education();
        existing.setId(10L);
        existing.setUserId(2L);
        doReturn(existing).when(educationMapper).selectById(10L);

        BusinessException ex = assertThrows(BusinessException.class, () -> educationService.update(dto));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(educationMapper).selectById(10L);
        verify(educationService, never()).updateById(any(Education.class));
    }

    @Test
    void educationDelete_removeFail_shouldThrowDeleteFailed() {
        UserHolder.set(UserContext.of(1L, "u1"));
        Education existing = new Education();
        existing.setId(11L);
        existing.setUserId(1L);
        doReturn(existing).when(educationMapper).selectById(11L);
        doReturn(false).when(educationService).removeById(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> educationService.delete(11L));

        assertEquals(ErrorCode.EDUCATION_DELETE_FAILED.getCode(), ex.getCode());
        verify(educationService).removeById(11L);
    }

    @Test
    void careerUpdate_invalidDate_shouldThrowAndSkipUpdateById() {
        UserHolder.set(UserContext.of(1L, "u1"));
        CareerDTO dto = new CareerDTO();
        dto.setId(20L);
        Career existing = new Career();
        existing.setId(20L);
        existing.setUserId(1L);
        existing.setStartDate(LocalDate.of(2024, 5, 1));
        doReturn(existing).when(careerMapper).selectById(20L);
        doAnswer(invocation -> {
            Career target = invocation.getArgument(1);
            target.setEndDate(LocalDate.of(2024, 4, 1));
            return null;
        }).when(careerStructMapper).updateEntityFromDto(any(CareerDTO.class), any(Career.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> careerService.update(dto));

        assertEquals(ErrorCode.CAREER_LEAVE_TIME_INVALID.getCode(), ex.getCode());
        verify(careerStructMapper).updateEntityFromDto(dto, existing);
        verify(careerService, never()).updateById(any(Career.class));
    }

    @Test
    void projectDelete_notOwner_shouldThrowNoPermissionAndSkipRemove() {
        UserHolder.set(UserContext.of(1L, "u1"));
        ProjectExperience existing = new ProjectExperience();
        existing.setId(30L);
        existing.setUserId(9L);
        doReturn(existing).when(projectExperienceMapper).selectById(30L);

        BusinessException ex = assertThrows(BusinessException.class, () -> projectExperienceService.delete(30L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(projectExperienceMapper).selectById(30L);
        verify(projectExperienceService, never()).removeById(30L);
    }

    @Test
    void skillCreate_userNotLogin_shouldThrowAndSkipSave() {
        UserHolder.clear();
        SkillDTO dto = new SkillDTO();
        dto.setName("skill-x");

        BusinessException ex = assertThrows(BusinessException.class, () -> skillService.create(dto));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(skillService, never()).save(any(Skill.class));
    }

    @Test
    void skillDelete_removeFail_shouldThrowDeleteFailed() {
        UserHolder.set(UserContext.of(1L, "u1"));
        Skill existing = new Skill();
        existing.setId(40L);
        existing.setUserId(1L);
        existing.setName("java");
        doReturn(existing).when(skillMapper).selectById(40L);
        doReturn(false).when(skillService).removeById(40L);

        BusinessException ex = assertThrows(BusinessException.class, () -> skillService.delete(40L));

        assertEquals(ErrorCode.SKILL_DELETE_FAILED.getCode(), ex.getCode());
        verify(skillService).removeById(40L);
    }

    @Test
    void careerGetByUserId_userNotLogin_shouldThrowAndSkipMapperCall() {
        UserHolder.clear();

        BusinessException ex = assertThrows(BusinessException.class, () -> careerService.getByUserId());

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(careerMapper, never()).selectByUserId(any(), any());
    }
}

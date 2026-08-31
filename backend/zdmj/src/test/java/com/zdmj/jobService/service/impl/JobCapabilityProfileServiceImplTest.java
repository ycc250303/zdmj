package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.entity.JobCapabilityProfile;
import com.zdmj.jobService.service.JobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobCapabilityProfileServiceImplTest {

    @Mock
    private JobService jobService;
    @Mock
    private ChatUtil chatUtil;

    private JobCapabilityProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        profileService = spy(new JobCapabilityProfileServiceImpl(jobService, chatUtil));
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void profile_generate_fail_shouldThrow10002() {
        Long jobId = 11L;
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doThrow(new RuntimeException("llm down")).when(chatUtil)
                .chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCapabilityProfileResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> profileService.getJobCapabilityProfile(jobId));

        assertEquals(ErrorCode.JOB_CAPABILITY_PROFILE_GENERATION_FAILED.getCode(), ex.getCode());
        verify(jobService).getDetail(jobId);
        verify(chatUtil).chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCapabilityProfileResponse.class));
        verify(profileService, never()).save(any(JobCapabilityProfile.class));
    }

    @Test
    void profile_generate_update_whenExisting_shouldUpdateAndReturnDto() {
        Long jobId = 12L;
        JobCapabilityProfileResponse aiResult = new JobCapabilityProfileResponse();
        aiResult.setProfessionalSkills("Java/Spring");
        aiResult.setSummary("summary");
        aiResult.setStrengths(List.of("基础扎实"));
        aiResult.setMissingSkills(List.of("分布式"));
        aiResult.setWeakEvidenceItems(List.of("高并发案例"));
        JobCapabilityProfile existing = new JobCapabilityProfile();
        existing.setId(900L);

        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(aiResult).when(chatUtil).chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCapabilityProfileResponse.class));
        doReturn(existing).when(profileService).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(profileService).updateById(any(JobCapabilityProfile.class));

        JobCapabilityProfileResponse result = profileService.getJobCapabilityProfile(jobId);

        assertEquals("Java/Spring", result.getProfessionalSkills());
        assertEquals("summary", result.getSummary());
        assertEquals(List.of("基础扎实"), result.getStrengths());
        verify(profileService).updateById(any(JobCapabilityProfile.class));
        verify(profileService, never()).save(any(JobCapabilityProfile.class));
    }

    @Test
    void profile_generate_create_whenNoExisting_shouldSaveAndReturnDto() {
        Long jobId = 17L;
        JobCapabilityProfileResponse aiResult = new JobCapabilityProfileResponse();
        aiResult.setProfessionalSkills("Python/FastAPI");
        aiResult.setSummary("new-profile");
        aiResult.setStrengths(List.of("工程化"));
        aiResult.setMissingSkills(List.of("分布式"));
        aiResult.setWeakEvidenceItems(List.of("高并发"));

        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(aiResult).when(chatUtil).chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCapabilityProfileResponse.class));
        doReturn(null).when(profileService).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(profileService).save(any(JobCapabilityProfile.class));

        JobCapabilityProfileResponse result = profileService.getJobCapabilityProfile(jobId);

        assertEquals("Python/FastAPI", result.getProfessionalSkills());
        assertEquals("new-profile", result.getSummary());
        assertEquals(List.of("工程化"), result.getStrengths());
        verify(profileService).save(any(JobCapabilityProfile.class));
        verify(profileService, never()).updateById(any(JobCapabilityProfile.class));
    }

    @Test
    void profile_notFound_getJobCapabilityProfileOrNull_shouldReturnNullAndVerify() {
        Long jobId = 13L;
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(null).when(profileService).getOne(any(LambdaQueryWrapper.class));

        JobCapabilityProfileResponse result = profileService.getJobCapabilityProfileOrNull(jobId);

        assertNull(result);
        verify(jobService).getDetail(jobId);
        verify(profileService).getOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void profile_fail_getJobCapabilityProfileOrNull_whenJobDetailNull_shouldThrow10001() {
        Long jobId = 14L;
        doReturn(null).when(jobService).getDetail(jobId);

        BusinessException ex = assertThrows(BusinessException.class, () -> profileService.getJobCapabilityProfileOrNull(jobId));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(jobService).getDetail(jobId);
        verify(profileService, never()).getOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void profile_getDetail_getJobCapabilityProfileOrNull_whenExists_shouldHydrateListFields() {
        Long jobId = 15L;
        JobCapabilityProfile entity = new JobCapabilityProfile();
        entity.setPromptName("JOB_REQUIREMENT_JAVA");
        entity.setProfessionalSkills("Java");
        entity.setStrengths(List.of("编码能力"));
        entity.setMissingSkills(List.of("分布式"));
        entity.setWeakEvidenceItems(List.of("项目深度"));
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(entity).when(profileService).getOne(any(LambdaQueryWrapper.class));

        JobCapabilityProfileResponse result = profileService.getJobCapabilityProfileOrNull(jobId);

        assertNotNull(result);
        assertEquals("Java", result.getProfessionalSkills());
        assertEquals(List.of("编码能力"), result.getStrengths());
        assertEquals(List.of("分布式"), result.getMissingSkills());
        verify(jobService).getDetail(jobId);
        verify(profileService).getOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void profile_getDetail_getJobCapabilityProfileOrNull_whenListFieldsNull_shouldReturnBasicFields() {
        Long jobId = 16L;
        JobCapabilityProfile entity = new JobCapabilityProfile();
        entity.setPromptName("JOB_REQUIREMENT_JAVA");
        entity.setTargetRoleType("java");
        entity.setProfessionalSkills("Golang");
        entity.setSummary("no-lists");
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(entity).when(profileService).getOne(any(LambdaQueryWrapper.class));

        JobCapabilityProfileResponse result = profileService.getJobCapabilityProfileOrNull(jobId);

        assertEquals("Golang", result.getProfessionalSkills());
        assertEquals("no-lists", result.getSummary());
        assertNull(result.getStrengths());
        assertNull(result.getMissingSkills());
        assertNull(result.getWeakEvidenceItems());
        verify(profileService).getOne(any(LambdaQueryWrapper.class));
    }

    private JobListItemResponse buildJobDetail() {
        JobListItemResponse dto = new JobListItemResponse();
        dto.setJobName("Java后端");
        dto.setCompanyName("ZDMJ");
        dto.setDescription("Java Spring MySQL Redis");
        dto.setJobDuties(List.of("开发接口"));
        dto.setJobRequirements(List.of("熟悉Spring Boot"));
        dto.setKeywords(List.of("Java", "Spring"));
        dto.setCompanyIndustries(List.of("互联网"));
        return dto;
    }
}

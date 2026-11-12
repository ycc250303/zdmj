package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageDTO;
import com.zdmj.jobService.dto.JobRequest;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.dto.JobResponse;
import com.zdmj.jobService.entity.Company;
import com.zdmj.jobService.entity.Job;
import com.zdmj.jobService.enums.JobEmploymentEnum;
import com.zdmj.jobService.mapper.CompanyMapper;
import com.zdmj.jobService.mapper.JobMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {
    @Mock
    private CompanyMapper companyMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private JobMapper jobMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JobServiceImpl jobService;
    private static boolean tableInfoInitialized;

    @BeforeEach
    void setUp() {
        initMybatisPlusLambdaCache();
        jobService = spy(new JobServiceImpl(companyMapper, redisTemplate, objectMapper));
        ReflectionTestUtils.setField(jobService, "baseMapper", jobMapper);
    }

    @Test
    void getDetail_null_marker_notFound_shouldThrow10001() {
        Long jobId = 101L;
        when(redisTemplate.hasKey(nullMarkerKey(jobId))).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.getDetail(jobId));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(jobMapper, never()).selectDetailById(any());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void getDetail_db_miss_notFound_shouldSetNullCacheAndThrow10001() {
        Long jobId = 102L;
        when(redisTemplate.hasKey(nullMarkerKey(jobId))).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(detailKey(jobId))).thenReturn(null);
        doReturn(null).when(jobMapper).selectDetailById(jobId);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.getDetail(jobId));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(valueOps).set(nullMarkerKey(jobId), "1", 60L, TimeUnit.SECONDS);
    }

    @Test
    void getDetail_cache_hit_shouldReturnCachedAndSkipDb() throws JsonProcessingException {
        Long jobId = 103L;
        JobListItemResponse cached = new JobListItemResponse();
        cached.setJobName("cached-java");
        when(redisTemplate.hasKey(nullMarkerKey(jobId))).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(detailKey(jobId))).thenReturn(objectMapper.writeValueAsString(cached));

        JobListItemResponse result = jobService.getDetail(jobId);

        assertEquals("cached-java", result.getJobName());
        verify(jobMapper, never()).selectDetailById(any());
    }

    @Test
    void getDetail_db_hit_shouldCacheAndReturnDbValue() throws JsonProcessingException {
        Long jobId = 104L;
        JobListItemResponse dbValue = new JobListItemResponse();
        dbValue.setJobName("db-java");
        dbValue.setCompanyName("ACME");
        when(redisTemplate.hasKey(nullMarkerKey(jobId))).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(detailKey(jobId))).thenReturn(null);
        doReturn(dbValue).when(jobMapper).selectDetailById(jobId);

        JobListItemResponse result = jobService.getDetail(jobId);

        assertEquals("db-java", result.getJobName());
        assertEquals("ACME", result.getCompanyName());
        verify(jobMapper).selectDetailById(jobId);
        verify(valueOps).set(
                detailKey(jobId),
                objectMapper.writeValueAsString(dbValue),
                600L,
                TimeUnit.SECONDS);
    }

    @Test
    void getDetail_whenRedisFails_shouldPropagate() {
        Long jobId = 105L;
        when(redisTemplate.hasKey(nullMarkerKey(jobId)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThrows(RedisConnectionFailureException.class, () -> jobService.getDetail(jobId));

        verify(jobMapper, never()).selectDetailById(any());
    }

    @Test
    void getDetail_whenCacheJsonInvalid_shouldPropagate() {
        Long jobId = 106L;
        when(redisTemplate.hasKey(nullMarkerKey(jobId))).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(detailKey(jobId))).thenReturn("{");

        assertThrows(IllegalStateException.class, () -> jobService.getDetail(jobId));

        verify(jobMapper, never()).selectDetailById(any());
    }

    @Test
    void create_save_whenCompanyExists_shouldReuseCompanyAndInitLists() {
        JobRequest dto = new JobRequest();
        dto.setCompanyName("ZDMJ");
        dto.setJobName("Java");
        dto.setDescription("desc");
        Company company = new Company();
        company.setId(88L);
        company.setName("ZDMJ");
        doReturn(company).when(companyMapper).selectOne(any());
        doReturn(true).when(jobService).save(any(Job.class));

        JobResponse created = jobService.create(dto);

        assertEquals(88L, created.getCompanyId());
        assertEquals("desc", created.getDescription());
        assertNotNull(created.getKeywords());
        assertEquals("", created.getLink());
        verify(jobService).save(any(Job.class));
    }

    @Test
    void create_save_whenLinkProvided_shouldPersistLink() {
        JobRequest dto = new JobRequest();
        dto.setCompanyName("ZDMJ");
        dto.setJobName("Java");
        dto.setDescription("desc");
        dto.setLink("https://example.com/jobs/1");
        Company company = new Company();
        company.setId(88L);
        company.setName("ZDMJ");
        doReturn(company).when(companyMapper).selectOne(any());
        doReturn(true).when(jobService).save(any(Job.class));

        JobResponse created = jobService.create(dto);

        assertEquals("https://example.com/jobs/1", created.getLink());
        verify(jobService).save(any(Job.class));
    }

    @Test
    void delete_whenSuccess_shouldDeleteCacheAndNullMarker() {
        Long jobId = 501L;
        doReturn(true).when(jobService).removeById(jobId);

        jobService.delete(jobId);

        verify(redisTemplate).delete(detailKey(jobId));
        verify(redisTemplate).delete(nullMarkerKey(jobId));
    }

    @Test
    void create_save_whenCompanyNotExists_shouldInsertCompany() {
        JobRequest dto = new JobRequest();
        dto.setCompanyName("NEW");
        dto.setJobName("Backend");
        doReturn(null).when(companyMapper).selectOne(any());
        doAnswer(invocation -> {
            Company c = invocation.getArgument(0);
            c.setId(900L);
            return 1;
        }).when(companyMapper).insert(any(Company.class));
        doReturn(true).when(jobService).save(any(Job.class));

        JobResponse created = jobService.create(dto);

        assertEquals(900L, created.getCompanyId());
        verify(companyMapper).insert(any(Company.class));
        verify(jobService).save(any(Job.class));
    }

    @Test
    void create_fail_whenCompanyInsertThrows_shouldPropagateFail() {
        JobRequest dto = new JobRequest();
        dto.setCompanyName("BAD");
        dto.setJobName("Backend");
        doReturn(null).when(companyMapper).selectOne(any());
        doAnswer(invocation -> {
            throw new RuntimeException("insert fail");
        }).when(companyMapper).insert(any(Company.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> jobService.create(dto));

        assertEquals("insert fail", ex.getMessage());
        verify(companyMapper).insert(any(Company.class));
        verify(jobService, never()).save(any(Job.class));
    }

    @Test
    void update_notFound_shouldThrow10001AndSkipUpdate() {
        JobRequest dto = new JobRequest();
        dto.setId(405L);
        doReturn(null).when(jobMapper).selectById(405L);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.update(dto));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(jobService, never()).updateById(any(Job.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void delete_notFound_shouldThrow10001AndSkipCacheEvict() {
        Long jobId = 502L;
        doReturn(false).when(jobService).removeById(jobId);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.delete(jobId));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(redisTemplate, never()).delete(detailKey(jobId));
        verify(redisTemplate, never()).delete(nullMarkerKey(jobId));
    }

    @Test
    void getDetail_getPage_shouldNormalizeQueryAndClampLimit() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setPage(0);
        query.setLimit(1000);
        query.setCompanyName("  ACME  ");
        query.setJobName("  Java工程师 ");
        query.setCompanySizes(List.of());
        query.setIndustries(List.of());
        query.setFundingTypes(List.of());
        JobListItemResponse row = new JobListItemResponse();
        row.setJobName("Java工程师");
        Page<JobListItemResponse> mpPage = new Page<>(1, 100);
        mpPage.setRecords(List.of(row));
        mpPage.setTotal(1);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        PageDTO<JobListItemResponse> page = jobService.getPage(query);

        assertEquals(1, page.getPage());
        assertEquals(100, page.getLimit());
        assertEquals(1L, page.getTotal());
        assertEquals("Java工程师", page.getList().get(0).getJobName());
        verify(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));
    }

    @Test
    void getDetail_getPage_whenQueryNull_shouldUseDefaultPageAndLimit() {
        JobListItemResponse row = new JobListItemResponse();
        row.setJobName("默认分页岗位");
        Page<JobListItemResponse> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of(row));
        mpPage.setTotal(0);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        PageDTO<JobListItemResponse> page = jobService.getPage(null);

        assertEquals(1, page.getPage());
        assertEquals(20, page.getLimit());
        assertEquals(0L, page.getTotal());
        assertEquals("默认分页岗位", page.getList().get(0).getJobName());
        verify(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));
    }

    @Test
    void getDetail_getPage_profile_shouldTrimAndNullifyEmptyFilters() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setPage(2);
        query.setLimit(10);
        query.setCompanyName("  ACME  ");
        query.setJobName("  Java  ");
        query.setCompanySizes(List.of());
        query.setIndustries(List.of());
        query.setFundingTypes(List.of());
        Page<JobListItemResponse> mpPage = new Page<>(2, 10);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        jobService.getPage(query);

        ArgumentCaptor<JobPageQueryDTO> captor = ArgumentCaptor.forClass(JobPageQueryDTO.class);
        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(jobMapper).selectJobPage(pageCaptor.capture(), captor.capture());
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(10, pageCaptor.getValue().getSize());
        JobPageQueryDTO normalized = captor.getValue();
        assertEquals("ACME", normalized.getCompanyName());
        assertEquals("Java", normalized.getJobName());
        assertNull(normalized.getCompanySizes());
        assertNull(normalized.getIndustries());
        assertNull(normalized.getFundingTypes());
    }

    @Test
    void getPage_whenSalaryTypeWithoutEmployment_shouldSetResolvedSalaryType() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setSalaryType(3);
        query.setFilterSalaryMin(200000);
        query.setFilterSalaryMax(500000);
        Page<JobListItemResponse> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        jobService.getPage(query);

        ArgumentCaptor<JobPageQueryDTO> captor = ArgumentCaptor.forClass(JobPageQueryDTO.class);
        verify(jobMapper).selectJobPage(any(Page.class), captor.capture());
        JobPageQueryDTO normalized = captor.getValue();
        assertNull(normalized.getEmployment());
        assertEquals(3, normalized.getResolvedSalaryType());
        assertEquals(200000, normalized.getFilterSalaryMin());
        assertEquals(500000, normalized.getFilterSalaryMax());
    }

    @Test
    void getPage_whenEmploymentSet_shouldClearResolvedSalaryTypeAndKeepSalaryRange() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setEmployment(JobEmploymentEnum.FULL_TIME);
        query.setSalaryType(2);
        query.setFilterSalaryMin(15000);
        query.setFilterSalaryMax(30000);
        Page<JobListItemResponse> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        jobService.getPage(query);

        ArgumentCaptor<JobPageQueryDTO> captor = ArgumentCaptor.forClass(JobPageQueryDTO.class);
        verify(jobMapper).selectJobPage(any(Page.class), captor.capture());
        JobPageQueryDTO normalized = captor.getValue();
        assertEquals(JobEmploymentEnum.FULL_TIME, normalized.getEmployment());
        assertNull(normalized.getResolvedSalaryType());
        assertEquals(Boolean.TRUE, normalized.getFullTimeEmployment());
        assertEquals(15000, normalized.getFilterSalaryMin());
        assertEquals(30000, normalized.getFilterSalaryMax());
    }

    @Test
    void getPage_whenInternEmployment_shouldSetResolvedSalaryTypeOne() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setEmployment(JobEmploymentEnum.INTERN);
        Page<JobListItemResponse> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        jobService.getPage(query);

        ArgumentCaptor<JobPageQueryDTO> captor = ArgumentCaptor.forClass(JobPageQueryDTO.class);
        verify(jobMapper).selectJobPage(any(Page.class), captor.capture());
        JobPageQueryDTO normalized = captor.getValue();
        assertEquals(JobEmploymentEnum.INTERN, normalized.getEmployment());
        assertEquals(1, normalized.getResolvedSalaryType());
        assertNull(normalized.getFullTimeEmployment());
    }

    @Test
    void getPage_whenSalaryRangeWithoutType_shouldStripSalaryFilters() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setFilterSalaryMin(10000);
        query.setFilterSalaryMax(20000);
        Page<JobListItemResponse> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        jobService.getPage(query);

        ArgumentCaptor<JobPageQueryDTO> captor = ArgumentCaptor.forClass(JobPageQueryDTO.class);
        verify(jobMapper).selectJobPage(any(Page.class), captor.capture());
        JobPageQueryDTO normalized = captor.getValue();
        assertNull(normalized.getEmployment());
        assertNull(normalized.getResolvedSalaryType());
        assertNull(normalized.getFilterSalaryMin());
        assertNull(normalized.getFilterSalaryMax());
    }

    @Test
    void getPage_whenFilterSalaryMinGreaterThanMax_shouldThrow() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setEmployment(JobEmploymentEnum.FULL_TIME);
        query.setFilterSalaryMin(20000);
        query.setFilterSalaryMax(10000);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.getPage(query));
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertEquals("最低薪资不能大于最高薪资", ex.getMessage());
        verify(jobMapper, never()).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));
    }

    private static String detailKey(Long jobId) {
        return "job:detail:v2:" + jobId;
    }

    private static String nullMarkerKey(Long jobId) {
        return "null:value:job:detail:v2:" + jobId;
    }

    private static void initMybatisPlusLambdaCache() {
        if (tableInfoInitialized) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Company.class);
        tableInfoInitialized = true;
    }
}

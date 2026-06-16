package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zdmj.common.cache.RedisConstants;
import com.zdmj.common.cache.RedisUtil;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageDTO;
import com.zdmj.jobService.dto.JobDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.dto.JobPageQueryDTO;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {
    @Mock
    private CompanyMapper companyMapper;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private JobMapper jobMapper;

    private JobServiceImpl jobService;
    private static boolean tableInfoInitialized;

    @BeforeEach
    void setUp() {
        initMybatisPlusLambdaCache();
        jobService = spy(new JobServiceImpl(companyMapper, redisUtil));
        ReflectionTestUtils.setField(jobService, "baseMapper", jobMapper);
    }

    @Test
    void getDetail_null_marker_notFound_shouldThrow10001() {
        Long jobId = 101L;
        String key = RedisConstants.JOB_DETAIL_KEY + jobId;
        doReturn(true).when(redisUtil).isNullValue(key);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.getDetail(jobId));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(redisUtil).isNullValue(key);
        verify(jobMapper, never()).selectDetailById(any());
    }

    @Test
    void getDetail_db_miss_notFound_shouldSetNullCacheAndThrow10001() {
        Long jobId = 102L;
        String key = RedisConstants.JOB_DETAIL_KEY + jobId;
        doReturn(false).when(redisUtil).isNullValue(key);
        doReturn(null).when(redisUtil).get(key, JobListItemDTO.class);
        doReturn(null).when(jobMapper).selectDetailById(jobId);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.getDetail(jobId));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(redisUtil).setNullValue(key, RedisConstants.JOB_DETAIL_NULL_TTL);
    }

    @Test
    void getDetail_cache_hit_shouldReturnCachedAndSkipDb() {
        Long jobId = 103L;
        String key = RedisConstants.JOB_DETAIL_KEY + jobId;
        JobListItemDTO cached = new JobListItemDTO();
        cached.setJobName("cached-java");
        doReturn(false).when(redisUtil).isNullValue(key);
        doReturn(cached).when(redisUtil).get(key, JobListItemDTO.class);

        JobListItemDTO result = jobService.getDetail(jobId);

        assertEquals("cached-java", result.getJobName());
        verify(redisUtil).get(key, JobListItemDTO.class);
        verify(jobMapper, never()).selectDetailById(any());
    }

    @Test
    void getDetail_db_hit_shouldCacheAndReturnDbValue() {
        Long jobId = 104L;
        String key = RedisConstants.JOB_DETAIL_KEY + jobId;
        JobListItemDTO dbValue = new JobListItemDTO();
        dbValue.setJobName("db-java");
        dbValue.setCompanyName("ACME");
        doReturn(false).when(redisUtil).isNullValue(key);
        doReturn(null).when(redisUtil).get(key, JobListItemDTO.class);
        doReturn(dbValue).when(jobMapper).selectDetailById(jobId);

        JobListItemDTO result = jobService.getDetail(jobId);

        assertEquals("db-java", result.getJobName());
        assertEquals("ACME", result.getCompanyName());
        verify(jobMapper).selectDetailById(jobId);
        verify(redisUtil).set(key, dbValue, RedisConstants.JOB_DETAIL_TTL);
    }

    @Test
    void create_save_whenCompanyExists_shouldReuseCompanyAndInitLists() {
        JobDTO dto = new JobDTO();
        dto.setCompanyName("ZDMJ");
        dto.setJobName("Java");
        dto.setDescription("desc");
        Company company = new Company();
        company.setId(88L);
        company.setName("ZDMJ");
        doReturn(company).when(companyMapper).selectOne(any());
        doReturn(true).when(jobService).save(any(Job.class));

        Job created = jobService.create(dto);

        assertEquals(88L, created.getCompanyId());
        assertNotNull(created.getContent());
        assertNotNull(created.getRequirements());
        assertNotNull(created.getKeywords());
        assertEquals("", created.getLink());
        verify(jobService).save(any(Job.class));
    }

    @Test
    void create_save_whenLinkProvided_shouldPersistLink() {
        JobDTO dto = new JobDTO();
        dto.setCompanyName("ZDMJ");
        dto.setJobName("Java");
        dto.setDescription("desc");
        dto.setLink("https://example.com/jobs/1");
        Company company = new Company();
        company.setId(88L);
        company.setName("ZDMJ");
        doReturn(company).when(companyMapper).selectOne(any());
        doReturn(true).when(jobService).save(any(Job.class));

        Job created = jobService.create(dto);

        assertEquals("https://example.com/jobs/1", created.getLink());
        verify(jobService).save(any(Job.class));
    }

    @Test
    void update_notFound_shouldThrow10001() {
        JobDTO dto = new JobDTO();
        dto.setId(404L);
        doReturn(null).when(jobMapper).selectById(404L);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.update(dto));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(redisUtil, never()).delete(any());
    }

    @Test
    void delete_whenSuccess_shouldDeleteCacheAndNullMarker() {
        Long jobId = 501L;
        doReturn(true).when(jobService).removeById(jobId);

        jobService.delete(jobId);

        String key = RedisConstants.JOB_DETAIL_KEY + jobId;
        verify(redisUtil).delete(key);
        verify(redisUtil).deleteNullValue(key);
    }

    @Test
    void create_save_whenCompanyNotExists_shouldInsertCompany() {
        JobDTO dto = new JobDTO();
        dto.setCompanyName("NEW");
        dto.setJobName("Backend");
        doReturn(null).when(companyMapper).selectOne(any());
        doAnswer(invocation -> {
            Company c = invocation.getArgument(0);
            c.setId(900L);
            return 1;
        }).when(companyMapper).insert(any(Company.class));
        doReturn(true).when(jobService).save(any(Job.class));

        Job created = jobService.create(dto);

        assertEquals(900L, created.getCompanyId());
        verify(companyMapper).insert(any(Company.class));
        verify(jobService).save(any(Job.class));
    }

    @Test
    void create_fail_whenCompanyInsertThrows_shouldPropagateFail() {
        JobDTO dto = new JobDTO();
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
        JobDTO dto = new JobDTO();
        dto.setId(405L);
        doReturn(null).when(jobMapper).selectById(405L);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.update(dto));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        verify(jobService, never()).updateById(any(Job.class));
        verify(redisUtil, never()).delete(any());
    }

    @Test
    void delete_notFound_shouldThrow10001AndSkipCacheEvict() {
        Long jobId = 502L;
        doReturn(false).when(jobService).removeById(jobId);

        BusinessException ex = assertThrows(BusinessException.class, () -> jobService.delete(jobId));

        assertEquals(ErrorCode.JOB_NOT_FOUND.getCode(), ex.getCode());
        String key = RedisConstants.JOB_DETAIL_KEY + jobId;
        verify(redisUtil, never()).delete(key);
        verify(redisUtil, never()).deleteNullValue(key);
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
        JobListItemDTO row = new JobListItemDTO();
        row.setJobName("Java工程师");
        Page<JobListItemDTO> mpPage = new Page<>(1, 100);
        mpPage.setRecords(List.of(row));
        mpPage.setTotal(1);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        PageDTO<JobListItemDTO> page = jobService.getPage(query);

        assertEquals(1, page.getPage());
        assertEquals(100, page.getLimit());
        assertEquals(1L, page.getTotal());
        assertEquals("Java工程师", page.getList().get(0).getJobName());
        verify(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));
    }

    @Test
    void getDetail_getPage_whenQueryNull_shouldUseDefaultPageAndLimit() {
        JobListItemDTO row = new JobListItemDTO();
        row.setJobName("默认分页岗位");
        Page<JobListItemDTO> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of(row));
        mpPage.setTotal(0);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        PageDTO<JobListItemDTO> page = jobService.getPage(null);

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
        Page<JobListItemDTO> mpPage = new Page<>(2, 10);
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
    void getDetail_getPage_whenNegativePageAndLimit_shouldFallbackDefaults() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setPage(-3);
        query.setLimit(-8);
        Page<JobListItemDTO> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of());
        mpPage.setTotal(5);
        doReturn(mpPage).when(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));

        PageDTO<JobListItemDTO> page = jobService.getPage(query);

        assertEquals(1, page.getPage());
        assertEquals(20, page.getLimit());
        assertEquals(5L, page.getTotal());
        assertEquals(1, page.getTotalPages());
        verify(jobMapper).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));
    }

    @Test
    void getPage_whenSalaryTypeWithoutEmployment_shouldSetResolvedSalaryType() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setSalaryType(3);
        query.setFilterSalaryMin(200000);
        query.setFilterSalaryMax(500000);
        Page<JobListItemDTO> mpPage = new Page<>(1, 20);
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
        Page<JobListItemDTO> mpPage = new Page<>(1, 20);
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
        Page<JobListItemDTO> mpPage = new Page<>(1, 20);
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
        Page<JobListItemDTO> mpPage = new Page<>(1, 20);
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

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jobService.getPage(query));
        assertEquals("最低薪资不能大于最高薪资", ex.getMessage());
        verify(jobMapper, never()).selectJobPage(any(Page.class), any(JobPageQueryDTO.class));
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

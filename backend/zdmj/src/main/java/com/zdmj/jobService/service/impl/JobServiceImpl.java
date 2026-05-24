package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.cache.RedisUtil;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.cache.RedisConstants;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.dto.JobDTO;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.entity.Company;
import com.zdmj.jobService.entity.Job;
import com.zdmj.jobService.mapper.CompanyMapper;
import com.zdmj.jobService.mapper.JobMapper;
import com.zdmj.jobService.service.JobService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class JobServiceImpl extends ServiceImpl<JobMapper, Job> implements JobService {

    private final CompanyMapper companyMapper;
    private final RedisUtil redisCacheUtil;

    @Override
    public JobListItemDTO getDetail(Long id) {
        String key = RedisConstants.JOB_DETAIL_KEY + id;
        if (redisCacheUtil.isNullValue(key)) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        JobListItemDTO cached = redisCacheUtil.get(key, JobListItemDTO.class);
        if (cached != null) {
            return cached;
        }
        JobListItemDTO dto = baseMapper.selectDetailById(id);
        if (dto == null) {
            redisCacheUtil.setNullValue(key, RedisConstants.JOB_DETAIL_NULL_TTL);
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        redisCacheUtil.set(key, dto, RedisConstants.JOB_DETAIL_TTL);
        return dto;
    }

    @Override
    public PageDTO<JobListItemDTO> getPage(JobPageQueryDTO query) {
        JobPageQueryDTO q = query != null ? query : new JobPageQueryDTO();
        PageRequests.Normalized paging = PageRequests.normalize(q.getPage(), q.getLimit());

        q.setCompanySizes(emptyToNull(q.getCompanySizes()));
        q.setFundingTypes(emptyToNull(q.getFundingTypes()));
        q.setIndustries(emptyToNull(q.getIndustries()));
        q.setCompanyName(StringUtils.hasText(q.getCompanyName()) ? q.getCompanyName().trim() : null);
        q.setJobName(StringUtils.hasText(q.getJobName()) ? q.getJobName().trim() : null);
    
        return PageDTO.from(baseMapper.selectJobPage(PageRequests.toPage(paging), q));
    }

    private static <T> List<T> emptyToNull(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Job create(JobDTO dto) {
        Company company = resolveCompanyByName(dto);
        Job job = new Job();
        fillJobFromDto(job, dto, company);
        save(job);
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Job update(JobDTO dto) {
        Job existing = baseMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        Company company = resolveCompanyByName(dto);
        fillJobFromDto(existing, dto, company);
        updateById(existing);
        evictJobDetailCache(dto.getId());
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!removeById(id)) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        evictJobDetailCache(id);
    }

    private void fillJobFromDto(Job job, JobDTO dto, Company company) {
        patchJobFromDto(dto, job);
        job.setCompanyId(company.getId());
        job.setCompanyName(company.getName());
        if (job.getContent() == null) {
            job.setContent(new ArrayList<>());
        }
        if (job.getRequirements() == null) {
            job.setRequirements(new ArrayList<>());
        }
        if (job.getKeywords() == null) {
            job.setKeywords(new ArrayList<>());
        }
    }

    /**
     * 按公司名精确匹配，若不存在则自动创建
     */
    private Company resolveCompanyByName(JobDTO dto) {
        Company company = companyMapper.selectOne(
                new LambdaQueryWrapper<Company>().eq(Company::getName, dto.getCompanyName()));
        if (company != null) {
            return company;
        }
        Company created = new Company();
        created.setName(dto.getCompanyName());
        created.setSize(Objects.requireNonNullElse(dto.getCompanySize(), 1));
        created.setType(dto.getCompanyFundingType());
        created.setIndustries(dto.getCompanyIndustries() == null ? new ArrayList<>() : dto.getCompanyIndustries());
        created.setIntroduction(dto.getCompanyIntroduction());
        companyMapper.insert(created);
        return created;
    }

    private void patchJobFromDto(JobDTO dto, Job job) {
        if (dto.getJobName() != null) {
            job.setJobName(dto.getJobName());
        }
        if (dto.getDescription() != null) {
            job.setDescription(dto.getDescription());
        }
        if (dto.getLocation() != null) {
            job.setLocation(dto.getLocation());
        }
        if (dto.getSalaryMin() != null) {
            job.setSalaryMin(dto.getSalaryMin());
        }
        if (dto.getSalaryMax() != null) {
            job.setSalaryMax(dto.getSalaryMax());
        }
        if (dto.getSalaryType() != null) {
            job.setSalaryType(dto.getSalaryType());
        }
        if (dto.getLink() != null) {
            job.setLink(dto.getLink());
        }
        if (dto.getJobDuties() != null) {
            job.setContent(dto.getJobDuties());
        }
        if (dto.getJobRequirements() != null) {
            job.setRequirements(dto.getJobRequirements());
        }
        if (dto.getKeywords() != null) {
            job.setKeywords(dto.getKeywords());
        }
    }

    /**
     * 删除岗位详情缓存
     * 
     * @param id 岗位ID
     */
    private void evictJobDetailCache(Long id) {
        String key = RedisConstants.JOB_DETAIL_KEY + id;
        redisCacheUtil.delete(key);
        redisCacheUtil.deleteNullValue(key);
    }
}

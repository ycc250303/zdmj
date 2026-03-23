package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.cache.RedisCacheUtil;
import com.zdmj.common.cache.RedisConstants;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageResult;
import com.zdmj.jobService.dto.JobDetailDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.dto.JobDTO;
import com.zdmj.jobService.entity.Company;
import com.zdmj.jobService.entity.Job;
import com.zdmj.jobService.enums.CompanySizeEnum;
import com.zdmj.jobService.mapper.CompanyMapper;
import com.zdmj.jobService.mapper.JobMapper;
import com.zdmj.jobService.service.JobService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobServiceImpl extends ServiceImpl<JobMapper, Job> implements JobService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyMapper companyMapper;
    private final RedisCacheUtil redisCacheUtil;

    public JobServiceImpl(CompanyMapper companyMapper, RedisCacheUtil redisCacheUtil) {
        this.companyMapper = companyMapper;
        this.redisCacheUtil = redisCacheUtil;
    }

    @Override
    public JobDetailDTO getDetail(Long id) {
        String key = RedisConstants.JOB_DETAIL_KEY + id;
        if (redisCacheUtil.isNullValue(key)) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        JobDetailDTO cached = redisCacheUtil.get(key, JobDetailDTO.class);
        if (cached != null) {
            return cached;
        }
        JobDetailDTO dto = baseMapper.selectDetailById(id);
        if (dto == null) {
            redisCacheUtil.setNullValue(key, RedisConstants.JOB_DETAIL_NULL_TTL);
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        redisCacheUtil.set(key, dto, RedisConstants.JOB_DETAIL_TTL);
        return dto;
    }

    @Override
    public PageResult<JobListItemDTO> getPage(Integer page, Integer limit,
            List<Integer> companySizes,
            List<Integer> fundingTypes,
            List<String> industries) {
        int p = (page == null || page < 1) ? 1 : page;
        int l = (limit == null || limit < 1) ? 10 : Math.min(limit, MAX_PAGE_SIZE);
        int offset = (p - 1) * l;

        List<Integer> sizes = emptyToNull(companySizes);
        List<Integer> types = emptyToNull(fundingTypes);
        List<String> inds = emptyToNull(industries);

        List<JobListItemDTO> data = baseMapper.selectPage(offset, l, sizes, types, inds);
        Long total = baseMapper.countPage(sizes, types, inds);
        return PageResult.of(data, total, p, l);
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
        job.setJobName(dto.getJobName());
        job.setCompanyId(company.getId());
        job.setCompanyName(company.getName());
        job.setDescription(dto.getDescription());
        job.setLocation(dto.getLocation());
        job.setSalary(dto.getSalary());
        job.setLink(dto.getLink());
        job.setContent(dto.getContent());
        job.setRequirements(dto.getRequirements());
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
        created.setSize(dto.getCompanySize() == null ? CompanySizeEnum.BELOW_20.getCode() : dto.getCompanySize());
        created.setType(dto.getCompanyFundingType());
        created.setIndustries(dto.getCompanyIndustries() == null ? new ArrayList<>() : dto.getCompanyIndustries());
        created.setIntroduction(dto.getCompanyIntroduction());
        companyMapper.insert(created);
        return created;
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

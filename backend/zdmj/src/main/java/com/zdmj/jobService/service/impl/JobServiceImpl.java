package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.dto.JobRequest;
import com.zdmj.jobService.dto.JobResponse;
import com.zdmj.jobService.entity.Company;
import com.zdmj.jobService.entity.Job;
import com.zdmj.jobService.enums.JobEmploymentEnum;
import com.zdmj.jobService.mapper.CompanyMapper;
import com.zdmj.jobService.mapper.JobMapper;
import com.zdmj.jobService.service.JobService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class JobServiceImpl extends ServiceImpl<JobMapper, Job> implements JobService {

    /** 详情缓存键前缀，完整键为 {@code job:detail:{id}}。 */
    private static final String DETAIL_KEY_PREFIX = "job:detail:";

    /** 空值标记前缀。与详情键拼接后为 {@code null:value:job:detail:{id}}。 */
    private static final String NULL_VALUE_KEY_PREFIX = "null:value:";

    /** 详情缓存有效期，单位秒。 */
    private static final long DETAIL_TTL_SECONDS = 10L * 60;

    /** 岗位不存在时的空值标记有效期，单位秒。 */
    private static final long NULL_TTL_SECONDS = 60L;

    private final CompanyMapper companyMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 查询岗位详情。
     * 先读取空值标记，命中则抛出岗位不存在；再读取详情缓存，命中则反序列化后返回。
     * 缓存未命中时查询数据库。数据库没有记录时写入 60 秒空值标记并抛出岗位不存在。
     * 查到记录时写入 10 分钟详情缓存并返回。Redis 读写失败或缓存无法解析时异常向上抛出。
     *
     * @param id 岗位编号
     * @return 岗位详情
     */
    @Override
    public JobListItemResponse getDetail(Long id) {
        String detailKey = DETAIL_KEY_PREFIX + id;
        String nullMarkerKey = NULL_VALUE_KEY_PREFIX + detailKey;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(nullMarkerKey))) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        String cachedJson = redisTemplate.opsForValue().get(detailKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, JobListItemResponse.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("岗位详情缓存无法解析: " + detailKey, exception);
            }
        }
        JobListItemResponse dto = baseMapper.selectDetailById(id);
        if (dto == null) {
            redisTemplate.opsForValue().set(nullMarkerKey, "1", NULL_TTL_SECONDS, TimeUnit.SECONDS);
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        try {
            redisTemplate.opsForValue().set(
                    detailKey,
                    objectMapper.writeValueAsString(dto),
                    DETAIL_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("岗位详情无法写入缓存: " + detailKey, exception);
        }
        return dto;
    }

    @Override
    public PageDTO<JobListItemResponse> getPage(JobPageQueryDTO query) {
        JobPageQueryDTO q = query != null ? query : new JobPageQueryDTO();
        PageRequests.Normalized paging = PageRequests.normalize(q.getPage(), q.getLimit());

        q.setCompanySizes(emptyToNull(q.getCompanySizes()));
        q.setFundingTypes(emptyToNull(q.getFundingTypes()));
        q.setIndustries(emptyToNull(q.getIndustries()));
        q.setCompanyName(StringUtils.hasText(q.getCompanyName()) ? q.getCompanyName().trim() : null);
        q.setJobName(StringUtils.hasText(q.getJobName()) ? q.getJobName().trim() : null);
        resolveSalaryQuery(q);

        return PageDTO.from(baseMapper.selectJobPage(PageRequests.toPage(paging), q));
    }

    /**
     * 用工类型与 salaryType 互斥；薪资区间仅在已明确薪资单位时生效。
     */
    private static void resolveSalaryQuery(JobPageQueryDTO q) {
        q.setFullTimeEmployment(null);
        if (q.getEmployment() != null) {
            if (q.getEmployment() == JobEmploymentEnum.INTERN) {
                q.setResolvedSalaryType(1);
            } else {
                q.setResolvedSalaryType(null);
                q.setFullTimeEmployment(true);
            }
        } else if (q.getSalaryType() != null) {
            int type = q.getSalaryType();
            if (type < 1 || type > 3) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "salaryType 必须为 1、2 或 3");
            }
            q.setResolvedSalaryType(type);
        } else {
            q.setResolvedSalaryType(null);
        }
        if (q.getEmployment() == null && q.getResolvedSalaryType() == null && !Boolean.TRUE.equals(q.getFullTimeEmployment())) {
            q.setFilterSalaryMin(null);
            q.setFilterSalaryMax(null);
        } else {
            validateSalaryFilterRange(q);
        }
    }

    private static void validateSalaryFilterRange(JobPageQueryDTO q) {
        Integer min = q.getFilterSalaryMin();
        Integer max = q.getFilterSalaryMax();
        if (min != null && max != null && min > max) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "最低薪资不能大于最高薪资");
        }
    }

    private static <T> List<T> emptyToNull(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResponse create(JobRequest dto) {
        Company company = resolveCompanyByName(dto);
        Job job = new Job();
        fillJobFromDto(job, dto, company);
        save(job);
        return convertToResponse(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResponse update(JobRequest dto) {
        Job existing = baseMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        Company company = resolveCompanyByName(dto);
        fillJobFromDto(existing, dto, company);
        updateById(existing);
        deleteDetailCache(dto.getId());
        return convertToResponse(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!removeById(id)) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        deleteDetailCache(id);
    }

    private void fillJobFromDto(Job job, JobRequest dto, Company company) {
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
        if (job.getLink() == null) {
            job.setLink("");
        }
    }

    /**
     * 按公司名精确匹配，若不存在则自动创建
     */
    private Company resolveCompanyByName(JobRequest dto) {
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

    private void patchJobFromDto(JobRequest dto, Job job) {
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

    private void deleteDetailCache(Long id) {
        String detailKey = DETAIL_KEY_PREFIX + id;
        redisTemplate.delete(detailKey);
        redisTemplate.delete(NULL_VALUE_KEY_PREFIX + detailKey);
    }

    private JobResponse convertToResponse(Job job) {
        JobResponse response = new JobResponse();
        BeanUtils.copyProperties(job, response);
        return response;
    }
}

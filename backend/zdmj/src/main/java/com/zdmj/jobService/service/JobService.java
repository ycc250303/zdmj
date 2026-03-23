package com.zdmj.jobService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.common.model.PageResult;
import com.zdmj.jobService.dto.JobDetailDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.dto.JobDTO;
import com.zdmj.jobService.entity.Job;

import java.util.List;

public interface JobService extends IService<Job> {

    JobDetailDTO getDetail(Long id);

    PageResult<JobListItemDTO> getPage(Integer page, Integer limit,
            List<Integer> companySizes,
            List<Integer> fundingTypes,
            List<String> industries);

    Job create(JobDTO dto);

    Job update(JobDTO dto);

    void delete(Long id);
}

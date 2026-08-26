package com.zdmj.jobService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.common.model.PageDTO;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.dto.JobRequest;
import com.zdmj.jobService.dto.JobResponse;
import com.zdmj.jobService.entity.Job;

public interface JobService extends IService<Job> {

    JobListItemResponse getDetail(Long id);

    PageDTO<JobListItemResponse> getPage(JobPageQueryDTO query);

    JobResponse create(JobRequest request);

    JobResponse update(JobRequest request);

    void delete(Long id);
}

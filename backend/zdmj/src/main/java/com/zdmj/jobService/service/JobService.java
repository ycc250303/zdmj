package com.zdmj.jobService.service;

import com.zdmj.common.model.PageDTO;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.dto.JobRequest;
import com.zdmj.jobService.dto.JobResponse;

public interface JobService {

    JobListItemResponse getDetail(Long id);

    PageDTO<JobListItemResponse> getPage(JobPageQueryDTO query);

    JobResponse create(JobRequest request);

    JobResponse update(JobRequest request);

    void delete(Long id);
}

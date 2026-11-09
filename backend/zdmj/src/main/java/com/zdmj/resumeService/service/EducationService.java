package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.EducationRequest;
import com.zdmj.resumeService.dto.EducationResponse;

/**
 * 教育经历服务接口
 */
public interface EducationService {

    EducationResponse create(EducationRequest educationRequest);

    EducationResponse update(EducationRequest educationRequest);

    void delete(Long id);
}

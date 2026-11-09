package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.CareerRequest;
import com.zdmj.resumeService.dto.CareerResponse;

/**
 * 工作/实习经历服务接口
 */
public interface CareerService {

    CareerResponse create(CareerRequest careerRequest);

    CareerResponse update(CareerRequest careerRequest);

    void delete(Long id);
}

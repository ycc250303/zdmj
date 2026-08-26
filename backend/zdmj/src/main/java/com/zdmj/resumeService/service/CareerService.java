package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.CareerRequest;
import com.zdmj.resumeService.dto.CareerResponse;

import java.util.List;

/**
 * 工作/实习经历服务接口
 */
public interface CareerService {

    CareerResponse create(CareerRequest careerRequest);

    CareerResponse getById(Long id);

    List<CareerResponse> getByUserId();

    CareerResponse update(CareerRequest careerRequest);

    void delete(Long id);
}

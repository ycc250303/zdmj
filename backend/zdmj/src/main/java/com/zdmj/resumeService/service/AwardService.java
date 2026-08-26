package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.AwardRequest;
import com.zdmj.resumeService.dto.AwardResponse;

import java.util.List;

public interface AwardService {

    AwardResponse create(AwardRequest awardRequest);

    AwardResponse getById(Long id);

    List<AwardResponse> getByUserId();

    AwardResponse update(AwardRequest awardRequest);

    void delete(Long id);
}

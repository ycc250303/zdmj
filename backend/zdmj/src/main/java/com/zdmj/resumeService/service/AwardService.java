package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.AwardRequest;
import com.zdmj.resumeService.dto.AwardResponse;

public interface AwardService {

    AwardResponse create(AwardRequest awardRequest);

    AwardResponse update(AwardRequest awardRequest);

    void delete(Long id);
}

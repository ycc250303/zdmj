package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.AwardDTO;
import com.zdmj.resumeService.entity.Award;

import java.util.List;

public interface AwardService {

    Award create(AwardDTO awardDTO);

    Award getById(Long id);

    List<Award> getByUserId();

    Award update(AwardDTO awardDTO);

    void delete(Long id);
}

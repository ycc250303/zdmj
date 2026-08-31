package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.SkillRequest;
import com.zdmj.resumeService.dto.SkillResponse;
import com.zdmj.resumeService.entity.Skill;

import java.util.List;

/**
 * 技能服务接口
 */
public interface SkillService {

    SkillResponse create(SkillRequest skillRequest);

    SkillResponse getById(Long id);

    List<SkillResponse> getByUserId();

    SkillResponse update(SkillRequest skillRequest);

    void delete(Long id);

    /**
     * 将已加载的实体转为响应（JSONB 文本 → List）。
     */
    SkillResponse toResponse(Skill skill);
}

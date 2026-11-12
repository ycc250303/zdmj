package com.zdmj.resumeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.resumeService.dto.EducationRequest;
import com.zdmj.resumeService.dto.EducationResponse;
import com.zdmj.resumeService.entity.Education;

/**
 * 教育经历服务接口
 */
public interface EducationService {

    EducationResponse create(CurrentActor actor, EducationRequest educationRequest);

    EducationResponse update(CurrentActor actor, EducationRequest educationRequest);

    /**
     * 把已确认归属的教育经历写成本次请求，并校验结束日期。
     *
     * @param existing 当前用户已加载的教育经历
     * @param educationRequest 本次教育经历
     * @return 更新后的教育经历
     */
    EducationResponse updateOwned(Education existing, EducationRequest educationRequest);

    void delete(CurrentActor actor, Long id);

    /**
     * 删除已确认属于当前用户的教育经历。
     *
     * @param id 教育经历编号
     */
    void deleteOwned(Long id);
}

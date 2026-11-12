package com.zdmj.resumeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.resumeService.dto.CareerRequest;
import com.zdmj.resumeService.dto.CareerResponse;
import com.zdmj.resumeService.entity.Career;

/**
 * 工作/实习经历服务接口
 */
public interface CareerService {

    CareerResponse create(CurrentActor actor, CareerRequest careerRequest);

    CareerResponse update(CurrentActor actor, CareerRequest careerRequest);

    /**
     * 把已确认归属的工作经历写成本次请求，并校验结束日期。
     *
     * @param existing 当前用户已加载的工作经历
     * @param careerRequest 本次工作经历
     * @return 更新后的工作经历
     */
    CareerResponse updateOwned(Career existing, CareerRequest careerRequest);

    void delete(CurrentActor actor, Long id);

    /**
     * 删除已确认属于当前用户的工作经历。
     *
     * @param id 工作经历编号
     */
    void deleteOwned(Long id);
}

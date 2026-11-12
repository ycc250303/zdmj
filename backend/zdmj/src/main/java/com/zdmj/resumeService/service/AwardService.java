package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.AwardRequest;
import com.zdmj.resumeService.dto.AwardResponse;
import com.zdmj.resumeService.entity.Award;

public interface AwardService {

    AwardResponse create(AwardRequest awardRequest);

    AwardResponse update(AwardRequest awardRequest);

    /**
     * 把已确认归属的获奖记录写成本次请求。
     *
     * @param existing 当前用户已加载的获奖记录
     * @param awardRequest 本次获奖内容
     * @return 更新后的获奖记录
     */
    AwardResponse updateOwned(Award existing, AwardRequest awardRequest);

    void delete(Long id);

    /**
     * 删除已确认属于当前用户的获奖记录。
     *
     * @param id 获奖记录编号
     */
    void deleteOwned(Long id);
}

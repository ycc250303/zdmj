package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.CareerDTO;
import com.zdmj.resumeService.entity.Career;
import java.util.List;

/**
 * 工作/实习经历服务接口
 */
public interface CareerService {

    /**
     * 创建工作经历
     *
     * @param career 工作经历实体
     * @return 创建的工作经历实体
     */
    Career create(CareerDTO careerDTO);

    /**
     * 根据ID查询工作经历
     *
     * @param id 工作经历ID
     * @return 工作经历实体
     */
    Career getById(Long id);

    /**
     * 根据用户ID查询所有工作经历
     *
     * @param userId 用户ID
     * @return 工作经历列表
     */
    List<Career> getByUserId();

    /**
     * 更新工作经历
     *
     * @param career 工作经历实体
     * @return 更新后的工作经历实体
     */
    Career update(CareerDTO careerDTO);

    /**
     * 删除工作经历
     *
     * @param id 工作经历ID
     */
    void delete(Long id);
}

package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.EducationDTO;
import com.zdmj.resumeService.entity.Education;

import java.util.List;

/**
 * 教育经历服务接口
 */
public interface EducationService {

    /**
     * 添加教育经历
     *
     * @param educationDTO 教育经历DTO
     * @return 教育经历实体
     */
    Education create(EducationDTO educationDTO);

    /**
     * 更新教育经历
     *
     * @param educationDTO 教育经历DTO（包含ID和要更新的字段）
     * @return 更新后的教育经历实体
     */
    Education update(EducationDTO educationDTO);

    /**
     * 删除教育经历
     *
     * @param id 教育经历ID
     */
    void delete(Long id);

    /**
     * 根据ID查询教育经历
     *
     * @param id 教育经历ID
     * @return 教育经历实体
     */
    Education getById(Long id);

    /**
     * 根据用户ID查询所有教育经历
     *
     * @return 教育经历列表
     */
    List<Education> getByUserId();
}

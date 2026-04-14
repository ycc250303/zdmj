package com.zdmj.jobService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.common.model.PageDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.dto.JobDTO;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.entity.Job;

public interface JobService extends IService<Job> {

    /** 
     * 查询岗位详情
     * @param id 岗位ID
     * @return 岗位详情
     */
    JobListItemDTO getDetail(Long id);

    /**
     * 分页查询岗位列表
     *
     * @param query 分页与筛选条件
     */
    PageDTO<JobListItemDTO> getPage(JobPageQueryDTO query);

    /** 
     * 创建岗位
     * @param dto 岗位DTO
     * @return 创建的岗位
     */
    Job create(JobDTO dto);

    /** 
     * 更新岗位
     * @param dto 岗位DTO
     * @return 更新的岗位
     */
    Job update(JobDTO dto);

    /** 
     * 删除岗位
     * @param id 岗位ID
     * @return 删除的岗位
     */
    void delete(Long id);
}

package com.zdmj.jobService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.common.model.PageDTO;
import com.zdmj.jobService.dto.JobDetailDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.dto.JobDTO;
import com.zdmj.jobService.entity.Job;

import java.util.List;

public interface JobService extends IService<Job> {

    /** 
     * 查询岗位详情
     * @param id 岗位ID
     * @return 岗位详情
     */
    JobDetailDTO getDetail(Long id);

    /** 
     * 查询岗位列表
     * @param page 页码
     * @param limit 每页条数
     * @param companySizes 公司规模
     * @param fundingTypes 融资类型
     * @param industries 行业
     * @param companyName 公司名称关键词（包含匹配，可为 null 表示不按公司名筛选）
     * @return 岗位列表
     */
    PageDTO<JobListItemDTO> getPage(Integer page, Integer limit,
            List<Integer> companySizes,
            List<Integer> fundingTypes,
            List<String> industries,
            String companyName);

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

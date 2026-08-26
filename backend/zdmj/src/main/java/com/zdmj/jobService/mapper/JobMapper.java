package com.zdmj.jobService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobMapper extends BaseMapper<Job> {

    /** 
     * 查询岗位详情
     * @param id 岗位ID
     * @return 岗位详情
     */
    JobListItemResponse selectDetailById(@Param("id") Long id);

    /**
     * 分页查询岗位列表（分页由 MyBatis-Plus 插件处理，勿在 SQL 中手写 LIMIT）。
     */
    IPage<JobListItemResponse> selectJobPage(IPage<JobListItemResponse> page, @Param("q") JobPageQueryDTO q);
}

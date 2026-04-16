package com.zdmj.jobService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface JobMapper extends BaseMapper<Job> {

    /** 
     * 查询岗位详情
     * @param id 岗位ID
     * @return 岗位详情
     */
    JobListItemDTO selectDetailById(@Param("id") Long id);

    /**
     * 查询岗位列表
     *
     * @param q      筛选条件
     * @param offset 偏移量
     * @param limit  每页条数
     */
    List<JobListItemDTO> selectPage(
            @Param("q") JobPageQueryDTO q,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);

    /**
     * 查询岗位总数
     *
     * @param q 筛选条件
     */
    Long countPage(@Param("q") JobPageQueryDTO q);
}

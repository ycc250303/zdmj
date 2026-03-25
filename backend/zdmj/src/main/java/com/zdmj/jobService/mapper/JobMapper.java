package com.zdmj.jobService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.jobService.dto.JobDetailDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface JobMapper extends BaseMapper<Job> {

    JobDetailDTO selectDetailById(@Param("id") Long id);

    List<JobListItemDTO> selectPage(
            @Param("offset") Integer offset,
            @Param("limit") Integer limit,
            @Param("companySizes") List<Integer> companySizes,
            @Param("fundingTypes") List<Integer> fundingTypes,
            @Param("industries") List<String> industries);

    Long countPage(
            @Param("companySizes") List<Integer> companySizes,
            @Param("fundingTypes") List<Integer> fundingTypes,
            @Param("industries") List<String> industries);
}

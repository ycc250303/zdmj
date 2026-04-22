package com.zdmj.jobService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.jobService.entity.JobCareerGraph;
import org.apache.ibatis.annotations.Mapper;

/**
 * 岗位关联图谱 Mapper。
 *
 * <p>不声明额外方法，1-to-1 关联（1 个岗位最多 1 张图谱）在 Service 层通过
 * {@code LambdaQueryWrapper.eq(JobCareerGraph::getJobId, ...)} + {@code getOne}/{@code save}/{@code updateById}
 * 维护，与 {@link com.zdmj.jobService.mapper.JobCapabilityProfileMapper} 保持一致风格。</p>
 */
@Mapper
public interface JobCareerGraphMapper extends BaseMapper<JobCareerGraph> {
}

package com.zdmj.matchService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zdmj.matchService.dto.JobStudentMatchListItemResponse;
import com.zdmj.matchService.entity.JobStudentMatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobStudentMatchMapper extends BaseMapper<JobStudentMatch> {

    /**
     * 分页查询当前用户的匹配记录（INNER JOIN jobs，岗位已删则不返回）。
     *
     * @param page   分页参数
     * @param userId 用户 ID
     * @return 列表项分页
     */
    IPage<JobStudentMatchListItemResponse> selectMyMatchPage(IPage<JobStudentMatchListItemResponse> page,
                                                        @Param("userId") Long userId);
}
